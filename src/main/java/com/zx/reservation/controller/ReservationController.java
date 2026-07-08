package com.zx.reservation.controller;

import com.zx.auth.dto.ApiResponse;
import com.zx.auth.security.AuthAttributes;
import com.zx.auth.security.AuthPrincipal;
import com.zx.reservation.dto.*;
import com.zx.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reservation")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/resources")
    public ApiResponse<PageResult<ResourceResponse>> listResources(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResponse.ok(reservationService.listResources(page, size));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/resources/{id}/seats")
    public ApiResponse<List<SeatResponse>> listSeats(@PathVariable Long id) {
        return ApiResponse.ok(reservationService.listSeats(id));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/slots/{slotId}/seats")
    public ApiResponse<List<SeatResponse>> listSlotSeats(@PathVariable Long slotId) {
        return ApiResponse.ok(reservationService.listSlotSeats(slotId));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/resources/{id}/slots")
    public ApiResponse<List<TimeSlotResponse>> listSlots(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.ok(reservationService.listSlots(id, date));
    }

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/orders")
    public ApiResponse<OrderResponse> createOrder(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @RequestBody CreateOrderRequest req
    ) {
        return ApiResponse.ok(reservationService.createOrder(principal, req));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/orders/mine")
    public ApiResponse<PageResult<OrderResponse>> listMyOrders(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResponse.ok(reservationService.listMyOrders(principal, status, page, size));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/orders/{id}")
    public ApiResponse<OrderResponse> getOrder(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(reservationService.getOrder(principal, id));
    }

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) CancelOrderRequest req
    ) {
        return ApiResponse.ok(reservationService.cancelOrder(principal, id, req));
    }
}
