package space.ducvinh.ticket_booking_service.reservation.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import space.ducvinh.ticket_booking_service.common.realtime.RealtimeBroadcastService;
import space.ducvinh.ticket_booking_service.inventory.service.InventoryService;
import space.ducvinh.ticket_booking_service.order.entity.OrderEntity;
import space.ducvinh.ticket_booking_service.order.enums.OrderStatus;
import space.ducvinh.ticket_booking_service.order.repository.OrderRepository;
import space.ducvinh.ticket_booking_service.reservation.entity.ReservationEntity;
import space.ducvinh.ticket_booking_service.reservation.entity.SeatClaimEntity;
import space.ducvinh.ticket_booking_service.reservation.enums.ReservationStatus;
import space.ducvinh.ticket_booking_service.reservation.repository.ReservationRepository;
import space.ducvinh.ticket_booking_service.reservation.repository.SeatClaimRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationExpirationService {

    private final ReservationRepository reservationRepository;
    private final SeatClaimRepository seatClaimRepository;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final RealtimeBroadcastService realtimeBroadcastService;

    @Transactional
    public boolean expireReservationIfStillActive(UUID reservationId) {
        Optional<ReservationEntity> opt = reservationRepository.findByIdForUpdate(reservationId);
        if (opt.isEmpty()) {
            return false;
        }
        ReservationEntity reservation = opt.get();
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            return false;
        }
        Instant now = Instant.now();
        if (reservation.getExpiresAt().isAfter(now)) {
            return false;
        }

        List<SeatClaimEntity> claims = seatClaimRepository.findByReservationId(reservationId);
        List<UUID> releasedSeatIds = claims.stream().map(SeatClaimEntity::getSeatId).toList();

        seatClaimRepository.deleteByReservationId(reservationId);
        reservation.setStatus(ReservationStatus.EXPIRED);
        reservationRepository.save(reservation);

        orderRepository.findByReservationId(reservationId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                order.setStatus(OrderStatus.EXPIRED);
                orderRepository.save(order);
            }
        });

        UUID eventId = reservation.getEventId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                inventoryService.evictSeatMapCache(eventId);
                if (!releasedSeatIds.isEmpty()) {
                    realtimeBroadcastService.broadcastSeatEvent(
                            eventId, "SEAT_RELEASED", releasedSeatIds, reservationId);
                }
            }
        });

        log.info("Expired reservation {} and released {} seat claims", reservationId, releasedSeatIds.size());
        return true;
    }
}
