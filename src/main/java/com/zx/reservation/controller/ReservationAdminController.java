package com.zx.reservation.controller;

import com.zx.auth.dto.ApiResponse;
import com.zx.reservation.dto.*;
import com.zx.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reservation/admin")
@RequiredArgsConstructor
public class ReservationAdminController {

    private final ReservationService reservationService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/resources")
    public ApiResponse<ResourceResponse> createResource(@RequestBody CreateResourceRequest req) {
        return ApiResponse.ok(reservationService.createResource(req));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/resources/{id}")
    public ApiResponse<ResourceResponse> updateResource(
            @PathVariable Long id,
            @RequestBody UpdateResourceRequest req
    ) {
        return ApiResponse.ok(reservationService.updateResource(id, req));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/resources/{id}")
    public ApiResponse<Void> disableResource(@PathVariable Long id) {
        reservationService.disableResource(id);
        return ApiResponse.ok(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/resources/{id}/seats/generate")
    public ApiResponse<Map<String, Object>> generateSeats(
            @PathVariable Long id,
            @RequestBody(required = false) GenerateSeatsRequest req
    ) {
        if (req == null) {
            req = new GenerateSeatsRequest();
        }
        int created = reservationService.generateSeats(id, req);
        return ApiResponse.ok(Map.of("created", created));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/resources/{id}/slots/generate")
    public ApiResponse<Map<String, Object>> generateSlots(
            @PathVariable Long id,
            @RequestBody GenerateSlotsRequest req
    ) {
        int created = reservationService.generateSlots(id, req);
        return ApiResponse.ok(Map.of("created", created));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/orders")
    public ApiResponse<PageResult<OrderResponse>> listAllOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResponse.ok(reservationService.listAllOrders(status, page, size));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/orders/{id}/complete")
    public ApiResponse<OrderResponse> completeOrder(@PathVariable Long id) {
        return ApiResponse.ok(reservationService.completeOrder(id));
    }
}
