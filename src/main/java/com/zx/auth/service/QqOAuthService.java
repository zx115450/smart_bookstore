package com.zx.auth.service;

import com.zx.auth.config.AuthQqOAuthProperties;
import com.zx.auth.dto.LoginRequest;
import com.zx.auth.dto.LoginResponse;
import com.zx.auth.oauth.QqOAuthConstants;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QqOAuthService {

    private final AuthQqOAuthProperties properties;
    private final AuthRedisService authRedisService;
    private final AuthService authService;

    public Map<String, String> createState() {
        ensureConfigured();
        String state = UUID.randomUUID().toString().replace("-", "");
        authRedisService.saveOAuthState(state, properties.getStateTtlSeconds());

        Map<String, String> data = new LinkedHashMap<>();
        data.put("state", state);
        data.put("appId", properties.getAppId());
        data.put("redirectUri", properties.getRedirectUri());
        return data;
    }

    public void handleCallback(String code, String state, String clientIp, String userAgent,
                               HttpServletResponse response) throws IOException {
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            redirectError(response, "missing_code_or_state");
            return;
        }
        if (!authRedisService.hasOAuthState(state)) {
            redirectError(response, "invalid_state");
            return;
        }

        try {
            ensureConfigured();
            LoginRequest req = new LoginRequest();
            req.setLoginType(QqOAuthConstants.LOGIN_TYPE_QQ_OAUTH);
            req.setCode(code);
            req.setState(state);

            LoginResponse loginResponse = authService.login(req, clientIp, userAgent);
            if (loginResponse == null) {
                redirectError(response, "qq_oauth_failed");
                return;
            }
            redirectSuccess(response, loginResponse);
        } catch (IllegalArgumentException e) {
            if ("user_disabled".equals(e.getMessage())) {
                redirectError(response, "user_disabled");
            } else {
                log.warn("QQ OAuth callback rejected: {}", e.getMessage());
                redirectError(response, "qq_oauth_failed");
            }
        } catch (IllegalStateException e) {
            log.warn("QQ OAuth callback config error: {}", e.getMessage());
            redirectError(response, "qq_oauth_failed");
        }
    }

    private void redirectSuccess(HttpServletResponse response, LoginResponse loginResponse) throws IOException {
        String fragment = "accessToken=" + encode(loginResponse.getAccessToken())
                + "&refreshToken=" + encode(loginResponse.getRefreshToken())
                + "&expireIn=" + loginResponse.getExpireIn();
        response.sendRedirect(properties.getFrontendSuccessUrl() + "#" + fragment);
    }

    private void redirectError(HttpServletResponse response, String reason) throws IOException {
        response.sendRedirect(properties.getFrontendErrorUrl() + "?reason=" + encode(reason));
    }

    private String encode(String value) {
        return UriUtils.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private void ensureConfigured() {
        if (!StringUtils.hasText(properties.getAppId()) || !StringUtils.hasText(properties.getAppKey())) {
            throw new IllegalStateException("QQ OAuth 未配置 app-id / app-key");
        }
    }
}
