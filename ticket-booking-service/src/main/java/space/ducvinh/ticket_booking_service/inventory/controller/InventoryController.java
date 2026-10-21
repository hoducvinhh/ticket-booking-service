package space.ducvinh.ticket_booking_service.inventory.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.ducvinh.ticket_booking_service.common.response.ApiResponse;
import space.ducvinh.ticket_booking_service.inventory.dto.InventoryDtos;
import space.ducvinh.ticket_booking_service.inventory.service.InventoryService;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{id}/seat-map")
    public ApiResponse<InventoryDtos.SeatMapResponse> getSeatMap(@PathVariable UUID id) {
        return ApiResponse.ok(inventoryService.getSeatMap(id));
    }

    @GetMapping("/{id}/availability")
    public ApiResponse<InventoryDtos.AvailabilityResponse> getAvailability(@PathVariable UUID id) {
        return ApiResponse.ok(inventoryService.getAvailability(id));
    }
}
