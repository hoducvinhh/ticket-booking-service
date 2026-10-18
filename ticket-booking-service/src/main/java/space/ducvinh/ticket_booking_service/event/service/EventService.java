package space.ducvinh.ticket_booking_service.event.service;

import java.io.IOException;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import org.springframework.data.jpa.domain.Specification;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import space.ducvinh.ticket_booking_service.common.audit.AuditLogService;
import space.ducvinh.ticket_booking_service.common.config.AppProperties;
import space.ducvinh.ticket_booking_service.common.exception.BadRequestException;
import space.ducvinh.ticket_booking_service.common.exception.BusinessRuleViolationException;
import space.ducvinh.ticket_booking_service.common.exception.ConflictException;
import space.ducvinh.ticket_booking_service.common.exception.ResourceNotFoundException;
import space.ducvinh.ticket_booking_service.common.outbox.OutboxService;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;
import space.ducvinh.ticket_booking_service.event.dto.EventDtos;
import space.ducvinh.ticket_booking_service.event.entity.EventEntity;
import space.ducvinh.ticket_booking_service.event.entity.EventStaffEntity;
import space.ducvinh.ticket_booking_service.event.enums.EventStatus;
import space.ducvinh.ticket_booking_service.event.repository.EventRepository;
import space.ducvinh.ticket_booking_service.event.repository.EventStaffRepository;
import space.ducvinh.ticket_booking_service.order.entity.OrderEntity;
import space.ducvinh.ticket_booking_service.order.enums.OrderStatus;
import space.ducvinh.ticket_booking_service.order.repository.OrderRepository;
import space.ducvinh.ticket_booking_service.reservation.repository.ReservationRepository;
import space.ducvinh.ticket_booking_service.user.entity.UserEntity;
import space.ducvinh.ticket_booking_service.user.enums.RoleName;
import space.ducvinh.ticket_booking_service.user.repository.UserRepository;
import space.ducvinh.ticket_booking_service.venue.entity.TicketSectionEntity;
import space.ducvinh.ticket_booking_service.venue.entity.VenueEntity;
import space.ducvinh.ticket_booking_service.venue.repository.TicketSectionRepository;
import space.ducvinh.ticket_booking_service.venue.repository.VenueRepository;

@Service
@RequiredArgsConstructor
public class EventService {

    private static final List<EventStatus> PUBLIC_STATUSES = List.of(
            EventStatus.PUBLISHED, EventStatus.SALES_OPEN, EventStatus.SALES_CLOSED);

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final TicketSectionRepository sectionRepository;
    private final EventStaffRepository eventStaffRepository;
    private final ReservationRepository reservationRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OutboxService outboxService;
    private final AuditLogService auditLogService;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public Page<EventDtos.EventResponse> listPublicEvents(
            String category, String search, Instant fromTime, Instant toTime, Pageable pageable) {
        Specification<EventEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));
            predicates.add(root.get("status").in(PUBLIC_STATUSES));
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase(Locale.ROOT)));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern)));
            }
            if (fromTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), fromTime));
            }
            if (toTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startTime"), toTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return eventRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<EventDtos.EventResponse> listOrganizerEvents(UUID organizerId, Pageable pageable) {
        return eventRepository.findByOrganizerIdAndDeletedFalseOrderByCreatedAtDesc(organizerId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public EventDtos.EventResponse getEvent(UUID eventId) {
        EventEntity event = eventRepository.findByIdAndDeletedFalse(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        return toResponse(event);
    }

    @Transactional
    public EventDtos.EventResponse createEvent(UUID organizerId, EventDtos.CreateEventRequest req) {
        validateSchedule(req.getStartTime(), req.getEndTime(), req.getSalesStartTime(), req.getSalesEndTime());

        VenueEntity venue = venueRepository.findById(req.getVenueId())
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found"));

        String slug = resolveUniqueSlug(req.getSlug() != null && !req.getSlug().isBlank() ? req.getSlug() : req.getName());

        EventEntity event = EventEntity.builder()
                .organizerId(organizerId)
                .venueId(venue.getId())
                .name(req.getName().trim())
                .slug(slug)
                .category(req.getCategory())
                .description(req.getDescription())
                .bannerUrl(req.getBannerUrl())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .salesStartTime(req.getSalesStartTime())
                .salesEndTime(req.getSalesEndTime())
                .waitingRoomEnabled(req.isWaitingRoomEnabled())
                .maxConcurrentUsers(req.getMaxConcurrentUsers())
                .maxSeatsPerReservation(req.getMaxSeatsPerReservation())
                .status(EventStatus.DRAFT)
                .build();

        return toResponse(eventRepository.save(event));
    }

    @Transactional
    public EventDtos.EventResponse updateEvent(UUID eventId, EventDtos.UpdateEventRequest req) {
        EventEntity event = findOwnedEvent(eventId);
        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
            throw new BusinessRuleViolationException("Cannot modify a cancelled or completed event");
        }

        if (req.getVenueId() != null) {
            venueRepository.findById(req.getVenueId())
                    .orElseThrow(() -> new ResourceNotFoundException("Venue not found"));
            event.setVenueId(req.getVenueId());
        }
        if (req.getName() != null && !req.getName().isBlank()) event.setName(req.getName().trim());
        if (req.getCategory() != null) event.setCategory(req.getCategory());
        if (req.getDescription() != null) event.setDescription(req.getDescription());
        if (req.getBannerUrl() != null) event.setBannerUrl(req.getBannerUrl());

        Instant start = req.getStartTime() != null ? req.getStartTime() : event.getStartTime();
        Instant end = req.getEndTime() != null ? req.getEndTime() : event.getEndTime();
        Instant salesStart = req.getSalesStartTime() != null ? req.getSalesStartTime() : event.getSalesStartTime();
        Instant salesEnd = req.getSalesEndTime() != null ? req.getSalesEndTime() : event.getSalesEndTime();
        validateSchedule(start, end, salesStart, salesEnd);

        event.setStartTime(start);
        event.setEndTime(end);
        event.setSalesStartTime(salesStart);
        event.setSalesEndTime(salesEnd);

        if (req.getWaitingRoomEnabled() != null) event.setWaitingRoomEnabled(req.getWaitingRoomEnabled());
        if (req.getMaxConcurrentUsers() != null) event.setMaxConcurrentUsers(req.getMaxConcurrentUsers());
        if (req.getMaxSeatsPerReservation() != null) event.setMaxSeatsPerReservation(req.getMaxSeatsPerReservation());

        return toResponse(eventRepository.save(event));
    }

    @Transactional
    public EventDtos.EventResponse publishEvent(UUID eventId) {
        EventEntity event = findOwnedEvent(eventId);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleViolationException("Cannot publish a cancelled event");
        }
        Instant now = Instant.now();
        if (!now.isBefore(event.getSalesStartTime()) && now.isBefore(event.getSalesEndTime())) {
            event.setStatus(EventStatus.SALES_OPEN);
        } else {
            event.setStatus(EventStatus.PUBLISHED);
        }
        return toResponse(eventRepository.save(event));
    }

    @Transactional
    public EventDtos.EventResponse hideEvent(UUID eventId) {
        EventEntity event = findOwnedEvent(eventId);
        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
            throw new BusinessRuleViolationException("Cannot hide a completed or cancelled event");
        }
        event.setStatus(EventStatus.DRAFT);
        return toResponse(eventRepository.save(event));
    }

    @Transactional
    public EventDtos.EventResponse cancelEvent(UUID eventId) {
        EventEntity event = findOwnedEvent(eventId);
        event.setStatus(EventStatus.CANCELLED);
        EventEntity saved = eventRepository.save(event);

        List<OrderEntity> paidOrders = orderRepository.findByEventIdAndStatus(eventId, OrderStatus.PAID);
        for (OrderEntity order : paidOrders) {
            order.setStatus(OrderStatus.REFUND_PENDING);
            orderRepository.save(order);
            outboxService.enqueue("ORDER", order.getId(), "EVENT_CANCELLED", Map.of(
                    "orderId", order.getId().toString(),
                    "userId", order.getUserId().toString(),
                    "eventId", eventId.toString(),
                    "eventName", event.getName()
            ));
        }

        auditLogService.record(SecurityUtils.requireCurrentUserId(), "EVENT_CANCELLED", "EVENT", eventId,
                Map.of("affectedPaidOrders", paidOrders.size()));
        return toResponse(saved);
    }

    @Transactional
    public void deleteEvent(UUID eventId) {
        EventEntity event = findOwnedEvent(eventId);
        if (reservationRepository.existsByEventId(eventId)) {
            throw new ConflictException("Cannot delete event that already has reservations or transactions");
        }
        event.setDeleted(true);
        eventRepository.save(event);
    }

    @Transactional
    public EventDtos.EventResponse uploadBanner(UUID eventId, MultipartFile file) {
        EventEntity event = findOwnedEvent(eventId);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Banner file is required");
        }
        try {
            Path dir = Paths.get(appProperties.getStorage().getUploadDir(), "banners").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String ext = "png";
            String original = file.getOriginalFilename();
            if (original != null && original.contains(".")) {
                ext = original.substring(original.lastIndexOf('.') + 1).replaceAll("[^a-zA-Z0-9]", "");
            }
            String filename = eventId + "-" + UUID.randomUUID() + "." + ext;
            Files.copy(file.getInputStream(), dir.resolve(filename));
            event.setBannerUrl("/uploads/banners/" + filename);
            return toResponse(eventRepository.save(event));
        } catch (IOException e) {
            throw new BadRequestException("Failed to store banner image");
        }
    }

    @Transactional
    public void assignStaff(UUID eventId, UUID staffId) {
        EventEntity event = findOwnedEvent(eventId);
        UserEntity staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found"));
        boolean isStaffOrAdmin = staff.getRoles().stream()
                .anyMatch(r -> r.getName().equals(RoleName.CHECKIN_STAFF.name())
                        || r.getName().equals(RoleName.ORGANIZER.name())
                        || r.getName().equals(RoleName.ADMIN.name()));
        if (!isStaffOrAdmin) {
            throw new BusinessRuleViolationException("Target user does not have CHECKIN_STAFF role");
        }
        if (eventStaffRepository.existsByEventIdAndStaffId(event.getId(), staffId)) {
            return;
        }
        eventStaffRepository.save(EventStaffEntity.builder()
                .eventId(event.getId())
                .staffId(staffId)
                .assignedBy(SecurityUtils.requireCurrentUserId())
                .build());
    }

    public EventEntity findOwnedEvent(UUID eventId) {
        EventEntity event = eventRepository.findByIdAndDeletedFalse(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        SecurityUtils.requireOwnerOrAdmin(event.getOrganizerId());
        return event;
    }

    private void validateSchedule(Instant start, Instant end, Instant salesStart, Instant salesEnd) {
        if (!end.isAfter(start)) {
            throw new BusinessRuleViolationException("Event end_time must be after start_time");
        }
        if (!salesEnd.isAfter(salesStart)) {
            throw new BusinessRuleViolationException("Sales end_time must be after sales_start_time");
        }
        if (salesEnd.isAfter(end)) {
            throw new BusinessRuleViolationException("Sales end_time cannot be after event end_time");
        }
    }

    private String resolveUniqueSlug(String base) {
        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (normalized.isBlank()) {
            normalized = "event";
        }
        String candidate = normalized;
        int suffix = 1;
        while (eventRepository.existsBySlug(candidate)) {
            candidate = normalized + "-" + (++suffix);
        }
        return candidate;
    }

    private EventDtos.EventResponse toResponse(EventEntity event) {
        VenueEntity venue = venueRepository.findById(event.getVenueId()).orElse(null);
        List<TicketSectionEntity> sections = sectionRepository.findByEventIdOrderByNameAsc(event.getId());
        List<EventDtos.SectionSummary> sectionSummaries = sections.stream()
                .map(s -> EventDtos.SectionSummary.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .price(s.getPrice())
                        .capacity(s.getCapacity())
                        .type(s.getType())
                        .build())
                .toList();
        return EventDtos.EventResponse.from(
                event,
                venue != null ? venue.getName() : null,
                venue != null ? venue.getAddress() : null,
                sectionSummaries);
    }
}
