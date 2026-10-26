package space.ducvinh.ticket_booking_service.reservation.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import space.ducvinh.ticket_booking_service.common.config.AppProperties;
import space.ducvinh.ticket_booking_service.common.config.RabbitMqConfig;
import space.ducvinh.ticket_booking_service.common.exception.BadRequestException;
import space.ducvinh.ticket_booking_service.common.exception.BusinessRuleViolationException;
import space.ducvinh.ticket_booking_service.common.exception.ConflictException;
import space.ducvinh.ticket_booking_service.common.exception.ForbiddenException;
import space.ducvinh.ticket_booking_service.common.exception.ResourceNotFoundException;
import space.ducvinh.ticket_booking_service.common.realtime.RealtimeBroadcastService;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;
import space.ducvinh.ticket_booking_service.event.entity.EventEntity;
import space.ducvinh.ticket_booking_service.event.enums.EventStatus;
import space.ducvinh.ticket_booking_service.event.repository.EventRepository;
import space.ducvinh.ticket_booking_service.inventory.service.InventoryService;
import space.ducvinh.ticket_booking_service.reservation.dto.ReservationDtos;
import space.ducvinh.ticket_booking_service.reservation.entity.ReservationEntity;
import space.ducvinh.ticket_booking_service.reservation.entity.ReservationItemEntity;
import space.ducvinh.ticket_booking_service.reservation.entity.SeatClaimEntity;
import space.ducvinh.ticket_booking_service.reservation.enums.ReservationStatus;
import space.ducvinh.ticket_booking_service.reservation.repository.ReservationItemRepository;
import space.ducvinh.ticket_booking_service.reservation.repository.ReservationRepository;
import space.ducvinh.ticket_booking_service.reservation.repository.SeatClaimRepository;
import space.ducvinh.ticket_booking_service.ticket.entity.TicketTypeEntity;
import space.ducvinh.ticket_booking_service.ticket.enums.TicketStatus;
import space.ducvinh.ticket_booking_service.ticket.repository.TicketRepository;
import space.ducvinh.ticket_booking_service.ticket.repository.TicketTypeRepository;
import space.ducvinh.ticket_booking_service.user.entity.UserEntity;
import space.ducvinh.ticket_booking_service.user.enums.UserStatus;
import space.ducvinh.ticket_booking_service.user.repository.UserRepository;
import space.ducvinh.ticket_booking_service.venue.entity.SeatEntity;
import space.ducvinh.ticket_booking_service.venue.entity.TicketSectionEntity;
import space.ducvinh.ticket_booking_service.venue.enums.SeatStatus;
import space.ducvinh.ticket_booking_service.venue.repository.SeatRepository;
import space.ducvinh.ticket_booking_service.venue.repository.TicketSectionRepository;
import space.ducvinh.ticket_booking_service.waitingroom.service.WaitingRoomService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final ReservationRepository reservationRepository;
    private final ReservationItemRepository reservationItemRepository;
    private final SeatClaimRepository seatClaimRepository;
    private final SeatRepository seatRepository;
    private final TicketSectionRepository sectionRepository;
    private final TicketRepository ticketRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final WaitingRoomService waitingRoomService;
    private final InventoryService inventoryService;
    private final RealtimeBroadcastService realtimeBroadcastService;
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final AppProperties appProperties;

    @Transactional
    public ReservationDtos.ReservationResponse reserveSeats(
            UUID userId,
            UUID eventId,
            ReservationDtos.CreateReservationRequest req,
            String admissionTokenHeader) {

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("Account is locked or disabled");
        }

        EventEntity event = eventRepository.findByIdAndDeletedFalse(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        Instant now = Instant.now();
        boolean statusAllowsSales = event.getStatus() == EventStatus.SALES_OPEN
                || event.getStatus() == EventStatus.PUBLISHED;
        if (!statusAllowsSales || now.isBefore(event.getSalesStartTime()) || !now.isBefore(event.getSalesEndTime())) {
            throw new BusinessRuleViolationException("SALES_CLOSED", "Event is not currently open for ticket sales");
        }

        if (event.isWaitingRoomEnabled()) {
            String token = (admissionTokenHeader != null && !admissionTokenHeader.isBlank())
                    ? admissionTokenHeader
                    : req.getAdmissionToken();
            waitingRoomService.validateAdmissionToken(eventId, userId, token);
        }

        if (req.getSeatIds() == null || req.getSeatIds().isEmpty()) {
            if (req.getTicketTypeId() != null) {
                return reserveGeneralAdmission(userId, event, req, now);
            }
            throw new BadRequestException("Either seatIds or ticketTypeId must be provided");
        }

        List<UUID> sortedSeatIds = req.getSeatIds().stream().distinct().sorted().toList();
        if (sortedSeatIds.isEmpty()) {
            throw new BadRequestException("At least one seat must be selected");
        }
        int maxSeats = Math.max(1, event.getMaxSeatsPerReservation());
        if (sortedSeatIds.size() > maxSeats) {
            throw new BusinessRuleViolationException("MAX_SEATS_EXCEEDED",
                    "You can reserve at most " + maxSeats + " seats per reservation");
        }

        // Optional Redis coordination lock (PostgreSQL FOR UPDATE + seat_claims PK remains source of truth)
        Map<String, String> acquiredLocks = acquireRedisSeatLocks(sortedSeatIds);
        try {
            List<SeatEntity> lockedSeats = seatRepository.findAllByIdInForUpdateOrderById(sortedSeatIds);
            if (lockedSeats.size() != sortedSeatIds.size()) {
                throw new ResourceNotFoundException("One or more seats do not exist");
            }

            Map<UUID, TicketSectionEntity> sectionsById = sectionRepository.findByEventIdOrderByNameAsc(eventId)
                    .stream().collect(Collectors.toMap(TicketSectionEntity::getId, s -> s));

            for (SeatEntity seat : lockedSeats) {
                if (!sectionsById.containsKey(seat.getSectionId())) {
                    throw new BusinessRuleViolationException("INVALID_SEAT_EVENT",
                            "Seat " + seat.getSeatCode() + " does not belong to this event");
                }
                if (seat.getStatus() == SeatStatus.BLOCKED || seat.getStatus() == SeatStatus.SOLD) {
                    throw new ConflictException("SEAT_UNAVAILABLE", "Seat " + seat.getSeatCode() + " is not available");
                }
                if (ticketRepository.existsBySeatIdAndStatusIn(
                        seat.getId(), List.of(TicketStatus.VALID, TicketStatus.CHECKED_IN))) {
                    throw new ConflictException("SEAT_SOLD", "Seat " + seat.getSeatCode() + " is already sold");
                }
            }

            // Check existing seat_claims and clean expired claims transactionally
            List<SeatClaimEntity> existingClaims = seatClaimRepository.findBySeatIdIn(sortedSeatIds);
            boolean cleanedAnyExpired = false;
            for (SeatClaimEntity claim : existingClaims) {
                if (claim.getExpiresAt().isAfter(now)) {
                    throw new ConflictException("SEAT_ALREADY_HELD", "One or more selected seats are already held");
                }
                seatClaimRepository.delete(claim);
                cleanedAnyExpired = true;
                reservationRepository.findByIdForUpdate(claim.getReservationId()).ifPresent(oldRes -> {
                    if (oldRes.getStatus() == ReservationStatus.ACTIVE) {
                        oldRes.setStatus(ReservationStatus.EXPIRED);
                        reservationRepository.save(oldRes);
                    }
                });
            }
            if (cleanedAnyExpired) {
                seatClaimRepository.flush();
            }

            long ttlSeconds = appProperties.getBooking().getReservationTtlSeconds();
            Instant expiresAt = now.plusSeconds(ttlSeconds);

            ReservationEntity reservation = reservationRepository.save(ReservationEntity.builder()
                    .userId(userId)
                    .eventId(eventId)
                    .status(ReservationStatus.ACTIVE)
                    .expiresAt(expiresAt)
                    .build());

            List<ReservationItemEntity> items = new ArrayList<>();
            List<SeatClaimEntity> newClaims = new ArrayList<>();
            List<ReservationDtos.ReservationItemDto> itemDtos = new ArrayList<>();
            long totalAmount = 0L;

            for (SeatEntity seat : lockedSeats) {
                TicketSectionEntity section = sectionsById.get(seat.getSectionId());
                long price = section.getPrice();
                totalAmount += price;

                items.add(ReservationItemEntity.builder()
                        .reservationId(reservation.getId())
                        .seatId(seat.getId())
                        .quantity(1)
                        .unitPrice(price)
                        .build());

                newClaims.add(SeatClaimEntity.builder()
                        .seatId(seat.getId())
                        .reservationId(reservation.getId())
                        .userId(userId)
                        .expiresAt(expiresAt)
                        .build());

                itemDtos.add(ReservationDtos.ReservationItemDto.builder()
                        .seatId(seat.getId())
                        .seatCode(seat.getSeatCode())
                        .sectionName(section.getName())
                        .unitPrice(price)
                        .build());
            }

            reservationItemRepository.saveAll(items);
            seatClaimRepository.saveAllAndFlush(newClaims);

            UUID resId = reservation.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        redisTemplate.opsForValue().set(
                                "reservation:" + resId + ":expiry",
                                "ACTIVE",
                                Duration.ofSeconds(ttlSeconds));
                    } catch (Exception ex) {
                        log.debug("Failed setting Redis TTL key for reservation {}: {}", resId, ex.getMessage());
                    }
                    inventoryService.evictSeatMapCache(eventId);
                    realtimeBroadcastService.broadcastSeatEvent(eventId, "SEAT_HELD", sortedSeatIds, resId);
                    try {
                        rabbitTemplate.convertAndSend(
                                RabbitMqConfig.EXCHANGE,
                                RabbitMqConfig.RK_RESERVATION_DELAY,
                                Map.of("reservationId", resId.toString()));
                    } catch (Exception ex) {
                        log.warn("Failed to enqueue delayed expiration for reservation {}: {}", resId, ex.getMessage());
                    }
                }
            });

            return ReservationDtos.ReservationResponse.builder()
                    .reservationId(resId)
                    .eventId(eventId)
                    .userId(userId)
                    .status(reservation.getStatus().name())
                    .expiresAt(expiresAt)
                    .totalAmount(totalAmount)
                    .createdAt(reservation.getCreatedAt())
                    .items(itemDtos)
                    .build();
        } finally {
            releaseRedisSeatLocks(acquiredLocks);
        }
    }

    private ReservationDtos.ReservationResponse reserveGeneralAdmission(
            UUID userId,
            EventEntity event,
            ReservationDtos.CreateReservationRequest req,
            Instant now) {
        int qty = req.getQuantity() != null ? req.getQuantity() : 1;
        int maxSeats = Math.max(1, event.getMaxSeatsPerReservation());
        if (qty > maxSeats) {
            throw new BusinessRuleViolationException("MAX_SEATS_EXCEEDED",
                    "You can reserve at most " + maxSeats + " tickets per reservation");
        }

        TicketTypeEntity ticketType = ticketTypeRepository.findByIdForUpdate(req.getTicketTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket type not found"));
        if (!ticketType.getEventId().equals(event.getId())) {
            throw new BusinessRuleViolationException("INVALID_TICKET_TYPE", "Ticket type does not belong to this event");
        }

        long activeReserved = reservationItemRepository.sumActiveReservedQuantity(ticketType.getId(), now);
        long available = ticketType.getTotalQuantity() - ticketType.getSoldQuantity() - activeReserved;
        if (available < qty) {
            throw new ConflictException("INSUFFICIENT_TICKETS", "Not enough tickets remaining for " + ticketType.getName());
        }

        long ttlSeconds = appProperties.getBooking().getReservationTtlSeconds();
        Instant expiresAt = now.plusSeconds(ttlSeconds);

        ReservationEntity reservation = reservationRepository.save(ReservationEntity.builder()
                .userId(userId)
                .eventId(event.getId())
                .status(ReservationStatus.ACTIVE)
                .expiresAt(expiresAt)
                .build());

        reservationItemRepository.save(ReservationItemEntity.builder()
                .reservationId(reservation.getId())
                .ticketTypeId(ticketType.getId())
                .quantity(qty)
                .unitPrice(ticketType.getPrice())
                .build());

        UUID resId = reservation.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    rabbitTemplate.convertAndSend(
                            RabbitMqConfig.EXCHANGE,
                            RabbitMqConfig.RK_RESERVATION_DELAY,
                            Map.of("reservationId", resId.toString()));
                } catch (Exception ex) {
                    log.warn("Failed to enqueue delayed expiration for GA reservation {}: {}", resId, ex.getMessage());
                }
            }
        });

        return ReservationDtos.ReservationResponse.builder()
                .reservationId(resId)
                .eventId(event.getId())
                .userId(userId)
                .status(reservation.getStatus().name())
                .expiresAt(expiresAt)
                .totalAmount(ticketType.getPrice() * qty)
                .createdAt(reservation.getCreatedAt())
                .items(List.of(ReservationDtos.ReservationItemDto.builder()
                        .ticketTypeId(ticketType.getId())
                        .sectionName(ticketType.getName())
                        .quantity(qty)
                        .unitPrice(ticketType.getPrice())
                        .build()))
                .build();
    }

    @Transactional(readOnly = true)
    public ReservationDtos.ReservationResponse getReservation(UUID reservationId) {
        ReservationEntity res = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        SecurityUtils.requireOwnerOrAdmin(res.getUserId());
        return toResponse(res);
    }

    @Transactional(readOnly = true)
    public Page<ReservationDtos.ReservationResponse> listMyReservations(UUID userId, Pageable pageable) {
        return reservationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(this::toResponse);
    }

    @Transactional
    public void cancelReservation(UUID reservationId) {
        ReservationEntity res = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        SecurityUtils.requireOwnerOrAdmin(res.getUserId());

        if (res.getStatus() != ReservationStatus.ACTIVE) {
            throw new BusinessRuleViolationException("Only ACTIVE reservations can be cancelled");
        }

        List<UUID> seatIds = seatClaimRepository.findByReservationId(reservationId).stream()
                .map(SeatClaimEntity::getSeatId)
                .toList();

        seatClaimRepository.deleteByReservationId(reservationId);
        res.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(res);

        UUID eventId = res.getEventId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    redisTemplate.delete("reservation:" + reservationId + ":expiry");
                } catch (Exception ignored) { }
                inventoryService.evictSeatMapCache(eventId);
                if (!seatIds.isEmpty()) {
                    realtimeBroadcastService.broadcastSeatEvent(eventId, "SEAT_RELEASED", seatIds, reservationId);
                }
            }
        });
    }

    private ReservationDtos.ReservationResponse toResponse(ReservationEntity res) {
        List<ReservationItemEntity> items = reservationItemRepository.findByReservationId(res.getId());
        Map<UUID, SeatEntity> seatMap = seatRepository.findAllById(
                        items.stream().map(ReservationItemEntity::getSeatId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(SeatEntity::getId, s -> s));
        Map<UUID, TicketSectionEntity> sectionMap = sectionRepository.findByEventIdOrderByNameAsc(res.getEventId())
                .stream().collect(Collectors.toMap(TicketSectionEntity::getId, s -> s));

        long total = 0L;
        List<ReservationDtos.ReservationItemDto> itemDtos = new ArrayList<>();
        for (ReservationItemEntity item : items) {
            total += item.getUnitPrice() * item.getQuantity();
            SeatEntity seat = item.getSeatId() != null ? seatMap.get(item.getSeatId()) : null;
            TicketSectionEntity sec = seat != null ? sectionMap.get(seat.getSectionId()) : null;
            itemDtos.add(ReservationDtos.ReservationItemDto.builder()
                    .seatId(item.getSeatId())
                    .seatCode(seat != null ? seat.getSeatCode() : null)
                    .sectionName(sec != null ? sec.getName() : null)
                    .unitPrice(item.getUnitPrice())
                    .build());
        }

        return ReservationDtos.ReservationResponse.builder()
                .reservationId(res.getId())
                .eventId(res.getEventId())
                .userId(res.getUserId())
                .status(res.getStatus().name())
                .expiresAt(res.getExpiresAt())
                .totalAmount(total)
                .createdAt(res.getCreatedAt())
                .items(itemDtos)
                .build();
    }

    private Map<String, String> acquireRedisSeatLocks(List<UUID> sortedSeatIds) {
        Map<String, String> acquired = new HashMap<>();
        try {
            for (UUID seatId : sortedSeatIds) {
                String key = "seat:" + seatId + ":lock";
                String token = UUID.randomUUID().toString();
                Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, token, Duration.ofSeconds(10));
                if (Boolean.TRUE.equals(ok)) {
                    acquired.put(key, token);
                }
            }
        } catch (Exception ex) {
            log.debug("Redis seat lock coordination skipped: {}", ex.getMessage());
        }
        return acquired;
    }

    private void releaseRedisSeatLocks(Map<String, String> acquiredLocks) {
        if (acquiredLocks.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : acquiredLocks.entrySet()) {
            try {
                redisTemplate.execute(UNLOCK_SCRIPT, List.of(entry.getKey()), entry.getValue());
            } catch (Exception ex) {
                log.debug("Failed releasing Redis lock {}: {}", entry.getKey(), ex.getMessage());
            }
        }
    }
}
