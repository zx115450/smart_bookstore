package com.zx.auth.service.login;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LoginHandlerFactory {
    private final List<LoginHandler> handlers;

    public LoginHandler getHandler(String loginType) {
        return handlers.stream()
                .filter(h -> h.supports(loginType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported loginType"));
    }
}

