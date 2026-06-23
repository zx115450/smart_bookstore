package com.zx.auth.service.sendcode;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SendCodeHandlerFactory {
    private final List<SendCodeHandler> handlers;

    public SendCodeHandler getHandler(String loginType) {
        return handlers.stream()
                .filter(h -> h.supports(loginType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported loginType"));
    }
}

