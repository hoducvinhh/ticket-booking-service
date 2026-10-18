package space.ducvinh.ticket_booking_service.event.service;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import space.ducvinh.ticket_booking_service.event.entity.EventEntity;
import space.ducvinh.ticket_booking_service.event.enums.EventStatus;
import space.ducvinh.ticket_booking_service.event.repository.EventRepository;

@Component
@RequiredArgsConstructor
public class EventStatusScheduler {

    private final EventRepository eventRepository;

    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void syncSalesWindowStatuses() {
        Instant now = Instant.now();
        List<EventEntity> readyToOpen = eventRepository.findPublishedEventsReadyToOpenSales(now);
        for (EventEntity e : readyToOpen) {
            e.setStatus(EventStatus.SALES_OPEN);
        }
        List<EventEntity> pastSalesEnd = eventRepository.findOpenEventsPastSalesEnd(now);
        for (EventEntity e : pastSalesEnd) {
            e.setStatus(EventStatus.SALES_CLOSED);
        }
        List<EventEntity> completed = eventRepository.findEventsReadyToComplete(now);
        for (EventEntity e : completed) {
            e.setStatus(EventStatus.COMPLETED);
        }
    }
}
