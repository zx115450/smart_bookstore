package com.zx.auth.controller;

import com.zx.auth.dto.ApiResponse;
import com.zx.auth.dto.LoginResponse;
import com.zx.auth.security.AuthAttributes;
import com.zx.auth.security.AuthPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/me")
    public ApiResponse<LoginResponse.UserInfo> me(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal
    ) {
        return ApiResponse.ok(new LoginResponse.UserInfo(
                principal.userId(),
                principal.username(),
                principal.roles()
        ));
    }
}
