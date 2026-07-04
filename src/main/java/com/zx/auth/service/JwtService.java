package com.zx.auth.service;

import com.zx.auth.config.AuthJwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    public static final String CLAIM_TYP = "typ";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_SID = "sid";
    public static final String CLAIM_ROLES = "roles";
    public static final String TOKEN_TYPE_ACCESS = "access";

    private final AuthJwtProperties jwtProperties;

    public String createAccessToken(Long userId, String username, Long sessionId, List<String> roles) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(jwtProperties.getAccessExpireSeconds());
        return Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_SID, sessionId)
                .claim(CLAIM_ROLES, roles == null ? List.of() : roles)
                .claim(CLAIM_TYP, TOKEN_TYPE_ACCESS)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey())
                .compact();
    }

    public AccessTokenClaims parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(jwtProperties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TYP, String.class))) {
                throw new InvalidAccessTokenException("invalid token type");
            }
            Long userId = Long.valueOf(claims.getSubject());
            String username = claims.get(CLAIM_USERNAME, String.class);
            Long sessionId = claims.get(CLAIM_SID, Number.class).longValue();
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get(CLAIM_ROLES, List.class);
            if (roles == null) {
                roles = List.of();
            }
            return new AccessTokenClaims(userId, username, sessionId, claims.getId(), roles);
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException("token expired");
        } catch (InvalidAccessTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidAccessTokenException("invalid token");
        }
    }

    public Instant getExpirationInstant(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getExpiration().toInstant();
    }

    private SecretKey signingKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public record AccessTokenClaims(Long userId, String username, Long sessionId, String jti, List<String> roles) {
    }

    public static class TokenExpiredException extends RuntimeException {
        public TokenExpiredException(String message) {
            super(message);
        }
    }

    public static class InvalidAccessTokenException extends RuntimeException {
        public InvalidAccessTokenException(String message) {
            super(message);
        }
    }
}

