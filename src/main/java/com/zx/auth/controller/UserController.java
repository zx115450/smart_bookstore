package com.zx.auth.controller;

import com.zx.common.dto.ApiResponse;
import com.zx.auth.dto.LoginResponse;
import com.zx.auth.repository.AuthUserRepository;
import com.zx.auth.security.AuthAttributes;
import com.zx.auth.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final AuthUserRepository userRepository;

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/me")
    public ApiResponse<LoginResponse.UserInfo> me(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal
    ) {
        BigDecimal balance = userRepository.findById(principal.userId())
                .map(u -> u.getBalance() != null ? u.getBalance() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
        return ApiResponse.ok(new LoginResponse.UserInfo(
                principal.userId(),
                principal.username(),
                principal.roles(),
                balance
        ));
    }
}
