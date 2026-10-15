package space.ducvinh.ticket_booking_service.venue.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.ducvinh.ticket_booking_service.common.exception.ConflictException;
import space.ducvinh.ticket_booking_service.common.exception.ResourceNotFoundException;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;
import space.ducvinh.ticket_booking_service.event.entity.EventEntity;
import space.ducvinh.ticket_booking_service.event.repository.EventRepository;
import space.ducvinh.ticket_booking_service.inventory.service.InventoryService;
import space.ducvinh.ticket_booking_service.venue.dto.VenueDtos;
import space.ducvinh.ticket_booking_service.venue.entity.SeatEntity;
import space.ducvinh.ticket_booking_service.venue.entity.TicketSectionEntity;
import space.ducvinh.ticket_booking_service.venue.entity.VenueEntity;
import space.ducvinh.ticket_booking_service.venue.enums.SeatStatus;
import space.ducvinh.ticket_booking_service.venue.repository.SeatRepository;
import space.ducvinh.ticket_booking_service.venue.repository.TicketSectionRepository;
import space.ducvinh.ticket_booking_service.venue.repository.VenueRepository;

@Service
@RequiredArgsConstructor
public class VenueService {

    private final VenueRepository venueRepository;
    private final TicketSectionRepository sectionRepository;
    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final InventoryService inventoryService;

    @Transactional(readOnly = true)
    public Page<VenueDtos.VenueResponse> listVenues(Pageable pageable) {
        return venueRepository.findAll(pageable).map(VenueDtos.VenueResponse::from);
    }

    @Transactional(readOnly = true)
    public VenueDtos.VenueResponse getVenue(UUID venueId) {
        VenueEntity venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found"));
        return VenueDtos.VenueResponse.from(venue);
    }

    @Transactional
    public VenueDtos.VenueResponse createVenue(UUID organizerId, VenueDtos.CreateVenueRequest req) {
        VenueEntity venue = VenueEntity.builder()
                .organizerId(organizerId)
                .name(req.getName().trim())
                .address(req.getAddress())
                .description(req.getDescription())
                .capacity(req.getCapacity())
                .build();
        return VenueDtos.VenueResponse.from(venueRepository.save(venue));
    }

    @Transactional
    public VenueDtos.SectionResponse createSection(UUID venueId, VenueDtos.CreateSectionRequest req) {
        VenueEntity venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found"));
        SecurityUtils.requireOwnerOrAdmin(venue.getOrganizerId());

        EventEntity event = eventRepository.findByIdAndDeletedFalse(req.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        SecurityUtils.requireOwnerOrAdmin(event.getOrganizerId());

        if (sectionRepository.existsByEventIdAndNameIgnoreCase(event.getId(), req.getName().trim())) {
            throw new ConflictException("Section name already exists for this event");
        }

        TicketSectionEntity section = TicketSectionEntity.builder()
                .eventId(event.getId())
                .name(req.getName().trim())
                .price(req.getPrice())
                .capacity(req.getCapacity())
                .type(req.getType() != null ? req.getType() : "RESERVED")
                .build();
        section = sectionRepository.save(section);
        inventoryService.evictSeatMapCache(event.getId());
        return VenueDtos.SectionResponse.from(section);
    }

    @Transactional
    public List<VenueDtos.SeatResponse> createSeats(UUID sectionId, VenueDtos.CreateSeatsRequest req) {
        TicketSectionEntity section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section not found"));
        EventEntity event = eventRepository.findByIdAndDeletedFalse(section.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        SecurityUtils.requireOwnerOrAdmin(event.getOrganizerId());

        List<SeatEntity> created = new ArrayList<>();
        for (VenueDtos.SeatItem item : req.getSeats()) {
            if (seatRepository.existsBySectionIdAndSeatCodeIgnoreCase(sectionId, item.getSeatCode().trim())) {
                throw new ConflictException("Duplicate seat code: " + item.getSeatCode());
            }
            created.add(SeatEntity.builder()
                    .sectionId(sectionId)
                    .seatCode(item.getSeatCode().trim())
                    .rowLabel(item.getRowLabel().trim())
                    .seatNumber(item.getSeatNumber())
                    .status(SeatStatus.AVAILABLE)
                    .build());
        }
        List<SeatEntity> saved = seatRepository.saveAll(created);
        inventoryService.evictSeatMapCache(event.getId());
        return saved.stream().map(VenueDtos.SeatResponse::from).toList();
    }

    @Transactional
    public VenueDtos.SeatResponse updateSeatStatus(UUID seatId, VenueDtos.UpdateSeatStatusRequest req) {
        SeatEntity seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found"));
        TicketSectionEntity section = sectionRepository.findById(seat.getSectionId())
                .orElseThrow(() -> new ResourceNotFoundException("Section not found"));
        EventEntity event = eventRepository.findByIdAndDeletedFalse(section.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        SecurityUtils.requireOwnerOrAdmin(event.getOrganizerId());

        seat.setStatus(req.getStatus());
        SeatEntity saved = seatRepository.save(seat);
        inventoryService.evictSeatMapCache(event.getId());
        return VenueDtos.SeatResponse.from(saved);
    }
}
