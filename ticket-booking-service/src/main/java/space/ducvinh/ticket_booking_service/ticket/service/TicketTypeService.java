package space.ducvinh.ticket_booking_service.ticket.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.ducvinh.ticket_booking_service.common.exception.ResourceNotFoundException;
import space.ducvinh.ticket_booking_service.common.security.SecurityUtils;
import space.ducvinh.ticket_booking_service.event.entity.EventEntity;
import space.ducvinh.ticket_booking_service.event.repository.EventRepository;
import space.ducvinh.ticket_booking_service.ticket.dto.TicketTypeDtos;
import space.ducvinh.ticket_booking_service.ticket.entity.TicketTypeEntity;
import space.ducvinh.ticket_booking_service.ticket.repository.TicketTypeRepository;

@Service
@RequiredArgsConstructor
public class TicketTypeService {

    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public List<TicketTypeDtos.TicketTypeResponse> listByEvent(UUID eventId) {
        return ticketTypeRepository.findByEventId(eventId).stream()
                .map(TicketTypeDtos.TicketTypeResponse::from)
                .toList();
    }

    @Transactional
    public TicketTypeDtos.TicketTypeResponse create(UUID eventId, TicketTypeDtos.CreateTicketTypeRequest req) {
        EventEntity event = eventRepository.findByIdAndDeletedFalse(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        SecurityUtils.requireOwnerOrAdmin(event.getOrganizerId());

        TicketTypeEntity entity = ticketTypeRepository.save(TicketTypeEntity.builder()
                .eventId(eventId)
                .sectionId(req.getSectionId())
                .name(req.getName().trim())
                .price(req.getPrice())
                .totalQuantity(req.getTotalQuantity())
                .soldQuantity(0)
                .saleMode(req.getSaleMode() != null ? req.getSaleMode() : "GENERAL_ADMISSION")
                .build());
        return TicketTypeDtos.TicketTypeResponse.from(entity);
    }
}
