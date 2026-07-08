package com.zx.auth.controller;

import com.zx.auth.dto.ApiResponse;
import com.zx.auth.service.QqOAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/oauth/qq")
@RequiredArgsConstructor
public class QqOAuthController {

    private final QqOAuthService qqOAuthService;

    @GetMapping("/state")
    public ApiResponse<Map<String, String>> state() {
        try {
            return ApiResponse.ok(qqOAuthService.createState());
        } catch (IllegalStateException e) {
            return ApiResponse.error(5001, e.getMessage());
        }
    }

    @GetMapping("/callback")
    public void callback(@RequestParam(required = false) String code,
                         @RequestParam(required = false) String state,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        qqOAuthService.handleCallback(
                code,
                state,
                request.getRemoteAddr(),
                request.getHeader(HttpHeaders.USER_AGENT),
                response
        );
    }
}
