package com.zx.auth.service.login;

import com.zx.auth.config.AuthQqOAuthProperties;
import com.zx.auth.dto.LoginRequest;
import com.zx.auth.entity.AuthUser;
import com.zx.auth.entity.AuthUserIdentity;
import com.zx.auth.oauth.QqOAuthClient;
import com.zx.auth.oauth.QqOAuthConstants;
import com.zx.auth.repository.AuthUserIdentityRepository;
import com.zx.auth.repository.AuthUserRepository;
import com.zx.auth.service.AuthRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class QqOAuthLoginHandler implements LoginHandler {

    private final AuthRedisService authRedisService;
    private final AuthQqOAuthProperties properties;
    private final QqOAuthClient qqOAuthClient;
    private final AuthUserIdentityRepository identityRepository;
    private final AuthUserRepository userRepository;

    @Override
    public boolean supports(String loginType) {
        return QqOAuthConstants.LOGIN_TYPE_QQ_OAUTH.equals(loginType);
    }

    @Override
    public AuthUser handle(LoginRequest req, String clientIp) {
        if (!StringUtils.hasText(req.getCode()) || !StringUtils.hasText(req.getState())) {
            return null;
        }
        if (!authRedisService.consumeOAuthState(req.getState())) {
            return null;
        }

        ensureConfigured();
        try {
            String accessToken = qqOAuthClient.exchangeCodeForAccessToken(req.getCode());
            String openid = qqOAuthClient.getOpenId(accessToken);
            String nickname = qqOAuthClient.getNickname(accessToken, openid);

            AuthUser user = findOrCreateUser(openid, nickname);
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new IllegalArgumentException("user_disabled");
            }

            req.setTarget(openid);
            return user;
        } catch (QqOAuthClient.QqOAuthException e) {
            log.warn("QQ OAuth login failed: {}", e.getMessage());
            return null;
        }
    }

    private AuthUser findOrCreateUser(String openid, String nickname) {
        return identityRepository
                .findByIdentityTypeAndIdentityValue(QqOAuthConstants.IDENTITY_TYPE_QQ_OPENID, openid)
                .map(identity -> {
                    AuthUser user = identity.getUser();
                    if (user != null) {
                        return user;
                    }
                    return userRepository.findById(identity.getUserId())
                            .orElseThrow(() -> new IllegalStateException("QQ 绑定用户不存在"));
                })
                .orElseGet(() -> createUserWithIdentity(openid, nickname));
    }

    private AuthUser createUserWithIdentity(String openid, String nickname) {
        AuthUser user = new AuthUser();
        user.setUsername(generateUniqueUsername(nickname, openid));
        user.setStatus(1);
        userRepository.save(user);

        AuthUserIdentity identity = new AuthUserIdentity();
        identity.setUser(user);
        identity.setIdentityType(QqOAuthConstants.IDENTITY_TYPE_QQ_OPENID);
        identity.setIdentityValue(openid);
        identity.setVerified(true);
        identity.setIsPrimary(true);
        identityRepository.save(identity);
        return user;
    }

    private String generateUniqueUsername(String nickname, String openid) {
        String base = sanitizeUsername(nickname);
        if (!StringUtils.hasText(base)) {
            base = "qq_" + openid.substring(Math.max(0, openid.length() - 8));
        }
        if (userRepository.findByUsername(base).isEmpty()) {
            return base;
        }
        for (int i = 1; i <= 99; i++) {
            String candidate = base + "_" + i;
            if (userRepository.findByUsername(candidate).isEmpty()) {
                return candidate;
            }
        }
        return "qq_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String sanitizeUsername(String nickname) {
        if (!StringUtils.hasText(nickname)) {
            return null;
        }
        String cleaned = nickname.replaceAll("[^\\w\\u4e00-\\u9fa5]", "").trim();
        if (cleaned.length() > 32) {
            cleaned = cleaned.substring(0, 32);
        }
        return cleaned.isBlank() ? null : cleaned;
    }

    private void ensureConfigured() {
        if (!StringUtils.hasText(properties.getAppId()) || !StringUtils.hasText(properties.getAppKey())) {
            throw new IllegalStateException("QQ OAuth 未配置 app-id / app-key");
        }
    }
}
