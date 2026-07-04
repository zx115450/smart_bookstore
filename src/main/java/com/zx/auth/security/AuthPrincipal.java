package com.zx.auth.security;

import java.util.List;

public record AuthPrincipal(Long userId, String username, Long sessionId, List<String> roles) {

    public AuthPrincipal {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
