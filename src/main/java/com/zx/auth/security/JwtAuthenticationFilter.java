package com.zx.auth.security;

import com.zx.auth.repository.AuthSessionRepository;
import com.zx.auth.repository.AuthUserRepository;
import com.zx.auth.service.AuthRedisService;
import com.zx.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/code/send",
            "/api/auth/logout",
            "/api/auth/oauth/qq/state",
            "/api/auth/oauth/qq/callback"
    );

    /**
     * 可选鉴权路径：无 Token 时放行（匿名），有 Token 时解析并写入 {@link AuthPrincipal}。
     * AI 聊天接口走此模式：匿名可对话，登录后可调用个人借阅等 Tool。
     */
    private static final List<String> OPTIONAL_AUTH_PATHS = List.of(
            "/api/ai/chat"
    );

    private final JwtService jwtService;
    private final AuthSessionRepository sessionRepository;
    private final AuthUserRepository userRepository;
    private final AuthRedisService authRedisService;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!request.getRequestURI().startsWith("/api/")) {
            return true;
        }
        return PUBLIC_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean optionalAuth = OPTIONAL_AUTH_PATHS.contains(request.getRequestURI());
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            if (optionalAuth) {
                // 匿名放行：不写入 AuthPrincipal，后续按匿名处理
                filterChain.doFilter(request, response);
                return;
            }
            writeUnauthorized(response, 2003, "missing or invalid authorization header");
            return;
        }

        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            if (optionalAuth) {
                filterChain.doFilter(request, response);
                return;
            }
            writeUnauthorized(response, 2003, "missing or invalid authorization header");
            return;
        }

        try {
            JwtService.AccessTokenClaims claims = jwtService.parseAccessToken(token);
            //Redis通过jti记录黑名单
            if (authRedisService.isAccessBlacklisted(claims.jti())) {
                writeUnauthorized(response, 2006, "token revoked");
                return;
            }
            //redis通过记录Session校验RefreshToken的合法性
            var sessionOpt = sessionRepository.findById(claims.sessionId());
            if (sessionOpt.isEmpty() || sessionOpt.get().getRevokedAt() != null) {
                writeUnauthorized(response, 2004, "session revoked or not found");
                return;
            }
            var session = sessionOpt.get();
            if (session.getAbsoluteExpiresAt() != null
                    && session.getAbsoluteExpiresAt().isBefore(LocalDateTime.now())) {
                writeUnauthorized(response, 2004, "session expired");
                return;
            }

            var userOpt = userRepository.findById(claims.userId());
            if (userOpt.isEmpty() || userOpt.get().getStatus() == null || userOpt.get().getStatus() != 1) {
                writeUnauthorized(response, 2005, "user disabled or not found");
                return;
            }

            List<String> roles = claims.roles();
            AuthPrincipal principal = new AuthPrincipal(
                    claims.userId(),
                    claims.username(),
                    claims.sessionId(),
                    roles
            );
            request.setAttribute(AuthAttributes.AUTH_USER, principal);

            var authorities = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (JwtService.TokenExpiredException e) {
            writeUnauthorized(response, 2002, "token expired");
        } catch (JwtService.InvalidAccessTokenException e) {
            writeUnauthorized(response, 2003, "invalid token");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void writeUnauthorized(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String escapedMessage = message.replace("\\", "\\\\").replace("\"", "\\\"");
        String body = String.format("{\"code\":%d,\"message\":\"%s\",\"data\":null}", code, escapedMessage);
        response.getWriter().write(body);
    }
}
