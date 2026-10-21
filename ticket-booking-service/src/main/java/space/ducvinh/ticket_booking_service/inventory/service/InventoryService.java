package space.ducvinh.ticket_booking_service.inventory.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.ducvinh.ticket_booking_service.common.exception.ResourceNotFoundException;
import space.ducvinh.ticket_booking_service.event.repository.EventRepository;
import space.ducvinh.ticket_booking_service.inventory.dto.InventoryDtos;
import space.ducvinh.ticket_booking_service.reservation.entity.SeatClaimEntity;
import space.ducvinh.ticket_booking_service.reservation.repository.SeatClaimRepository;
import space.ducvinh.ticket_booking_service.ticket.entity.TicketEntity;
import space.ducvinh.ticket_booking_service.ticket.enums.TicketStatus;
import space.ducvinh.ticket_booking_service.ticket.repository.TicketRepository;
import space.ducvinh.ticket_booking_service.venue.entity.SeatEntity;
import space.ducvinh.ticket_booking_service.venue.entity.TicketSectionEntity;
import space.ducvinh.ticket_booking_service.venue.enums.SeatStatus;
import space.ducvinh.ticket_booking_service.venue.repository.SeatRepository;
import space.ducvinh.ticket_booking_service.venue.repository.TicketSectionRepository;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final String SEAT_MAP_CACHE_PREFIX = "event:%s:seat-map";

    private final EventRepository eventRepository;
    private final TicketSectionRepository sectionRepository;
    private final SeatRepository seatRepository;
    private final SeatClaimRepository seatClaimRepository;
    private final TicketRepository ticketRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public InventoryDtos.SeatMapResponse getSeatMap(UUID eventId) {
        String cacheKey = String.format(SEAT_MAP_CACHE_PREFIX, eventId);
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, InventoryDtos.SeatMapResponse.class);
            }
        } catch (Exception ex) {
            log.debug("Seat map cache miss/parse issue for event={}: {}", eventId, ex.getMessage());
        }

        if (eventRepository.findByIdAndDeletedFalse(eventId).isEmpty()) {
            throw new ResourceNotFoundException("Event not found");
        }

        List<TicketSectionEntity> sections = sectionRepository.findByEventIdOrderByNameAsc(eventId);
        if (sections.isEmpty()) {
            return InventoryDtos.SeatMapResponse.builder().eventId(eventId).sections(List.of()).build();
        }

        List<UUID> sectionIds = sections.stream().map(TicketSectionEntity::getId).toList();
        List<SeatEntity> seats = seatRepository.findBySectionIdInOrderByRowLabelAscSeatNumberAsc(sectionIds);

        Instant now = Instant.now();
        Set<UUID> activeHeldSeatIds = seatClaimRepository.findActiveClaimsByEventId(eventId, now).stream()
                .map(SeatClaimEntity::getSeatId)
                .collect(Collectors.toSet());

        Set<UUID> soldSeatIds = ticketRepository
                .findByEventIdAndStatusIn(eventId, List.of(TicketStatus.VALID, TicketStatus.CHECKED_IN)).stream()
                .map(TicketEntity::getSeatId)
                .collect(Collectors.toSet());

        Map<UUID, List<SeatEntity>> seatsBySection = seats.stream()
                .collect(Collectors.groupingBy(SeatEntity::getSectionId));

        List<InventoryDtos.SectionSeatMap> sectionMaps = sections.stream().map(sec -> {
            List<InventoryDtos.SeatNode> nodes = seatsBySection.getOrDefault(sec.getId(), List.of()).stream()
                    .map(seat -> {
                        String effectiveStatus = resolveEffectiveStatus(seat, activeHeldSeatIds, soldSeatIds);
                        return InventoryDtos.SeatNode.builder()
                                .id(seat.getId())
                                .seatCode(seat.getSeatCode())
                                .rowLabel(seat.getRowLabel())
                                .seatNumber(seat.getSeatNumber())
                                .status(effectiveStatus)
                                .price(sec.getPrice())
                                .build();
                    })
                    .toList();
            return InventoryDtos.SectionSeatMap.builder()
                    .sectionId(sec.getId())
                    .sectionName(sec.getName())
                    .price(sec.getPrice())
                    .type(sec.getType())
                    .seats(nodes)
                    .build();
        }).toList();

        InventoryDtos.SeatMapResponse response = InventoryDtos.SeatMapResponse.builder()
                .eventId(eventId)
                .sections(sectionMaps)
                .build();

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), Duration.ofSeconds(15));
        } catch (Exception ex) {
            log.debug("Failed to cache seat map for event={}: {}", eventId, ex.getMessage());
        }
        return response;
    }

    @Transactional(readOnly = true)
    public InventoryDtos.AvailabilityResponse getAvailability(UUID eventId) {
        InventoryDtos.SeatMapResponse seatMap = getSeatMap(eventId);
        long total = 0, available = 0, held = 0, sold = 0, blocked = 0;
        for (InventoryDtos.SectionSeatMap sec : seatMap.getSections()) {
            for (InventoryDtos.SeatNode seat : sec.getSeats()) {
                total++;
                switch (seat.getStatus()) {
                    case "AVAILABLE" -> available++;
                    case "HELD" -> held++;
                    case "SOLD" -> sold++;
                    case "BLOCKED" -> blocked++;
                    default -> { }
                }
            }
        }
        return InventoryDtos.AvailabilityResponse.builder()
                .eventId(eventId)
                .totalSeats(total)
                .availableSeats(available)
                .heldSeats(held)
                .soldSeats(sold)
                .blockedSeats(blocked)
                .build();
    }

    public void evictSeatMapCache(UUID eventId) {
        try {
            redisTemplate.delete(String.format(SEAT_MAP_CACHE_PREFIX, eventId));
        } catch (Exception ex) {
            log.debug("Failed to evict seat map cache for event={}", eventId);
        }
    }

    private String resolveEffectiveStatus(SeatEntity seat, Set<UUID> activeHeldSeatIds, Set<UUID> soldSeatIds) {
        if (seat.getStatus() == SeatStatus.BLOCKED) {
            return SeatStatus.BLOCKED.name();
        }
        if (seat.getStatus() == SeatStatus.SOLD || soldSeatIds.contains(seat.getId())) {
            return SeatStatus.SOLD.name();
        }
        if (activeHeldSeatIds.contains(seat.getId())) {
            return SeatStatus.HELD.name();
        }
        return SeatStatus.AVAILABLE.name();
    }
}
