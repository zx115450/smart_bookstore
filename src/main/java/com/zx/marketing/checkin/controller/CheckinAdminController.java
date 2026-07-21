package com.zx.marketing.checkin.controller;

import com.zx.common.dto.ApiResponse;
import com.zx.marketing.checkin.dto.CheckinResponse;
import com.zx.marketing.checkin.service.CheckinService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservation/admin")
@RequiredArgsConstructor
public class CheckinAdminController {

    private final CheckinService checkinService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/resources/{id}/checkin-code")
    public ApiResponse<CheckinResponse.VenueCodeResponse> getDailyCheckinCode(@PathVariable Long id) {
        return ApiResponse.ok(checkinService.getDailyVenueCode(id));
    }
}
