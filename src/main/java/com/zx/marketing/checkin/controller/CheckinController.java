package com.zx.marketing.checkin.controller;

import com.zx.auth.dto.ApiResponse;
import com.zx.auth.security.AuthAttributes;
import com.zx.auth.security.AuthPrincipal;
import com.zx.marketing.checkin.dto.CheckinRequest;
import com.zx.marketing.checkin.dto.CheckinResponse;
import com.zx.marketing.checkin.service.CheckinService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/eligible")
    public ApiResponse<CheckinResponse.EligibleResponse> eligible(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @RequestParam Long venueId,
            @RequestParam String code
    ) {
        return ApiResponse.ok(checkinService.listEligible(principal, venueId, code));
    }

    @PreAuthorize("hasRole('USER')")
    @PostMapping
    public ApiResponse<CheckinResponse> checkin(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @RequestBody CheckinRequest req
    ) {
        return ApiResponse.ok(checkinService.checkin(principal, req));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/calendar")
    public ApiResponse<CheckinResponse.CalendarResponse> calendar(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @RequestParam(required = false) String month
    ) {
        return ApiResponse.ok(checkinService.getCalendar(principal, month));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/streak")
    public ApiResponse<CheckinResponse> streak(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal
    ) {
        return ApiResponse.ok(checkinService.getStreak(principal));
    }
}
