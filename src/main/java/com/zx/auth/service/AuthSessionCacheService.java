package com.zx.auth.service;

import com.zx.auth.entity.AuthSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * auth_session 的 Redis 读缓存（Cache-Aside）：MySQL 为权威数据源，Redis 加速按 sessionId / refresh jti 查询。
 * <p>
 * 维护两类 Key：{@code auth:session:id:{sessionId}} 存完整快照；
 * {@code auth:session:jti:{jti}} 存 sessionId 索引，供 Refresh 流程按 jti 反查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthSessionCacheService {

    /** 按 sessionId 存储 Session 快照，Value 为 {@link #serialize} 后的字符串。 */
    private static final String KEY_ID_PREFIX = "auth:session:id:";
    /** 按 refresh token 的 jti 存储 sessionId 索引，供 {@link #getByRefreshTokenJti} 使用。 */
    private static final String KEY_JTI_PREFIX = "auth:session:jti:";
    /** 快照字段分隔符，避免与普通文本冲突。 */
    private static final char SEP = '\u0001';

    private final StringRedisTemplate redis;

    @Value("${auth.session-cache.enabled:true}")
    private boolean enabled;

    /**
     * 按 sessionId 读取缓存；未启用、未命中或已过期时返回 empty，由调用方回源 MySQL。
     */
    public Optional<AuthSession> getById(Long sessionId) {
        if (!enabled || sessionId == null) {
            return Optional.empty();
        }
        return readSnapshot(KEY_ID_PREFIX + sessionId).map(AuthSessionCacheSnapshot::toEntity);
    }

    /**
     * 按 refresh token 的 jti 读取缓存：先查 jti→sessionId 索引，再 {@link #getById} 取完整快照。
     */
    public Optional<AuthSession> getByRefreshTokenJti(String jti) {
        if (!enabled || jti == null) {
            return Optional.empty();
        }
        String sessionIdStr = redis.opsForValue().get(KEY_JTI_PREFIX + jti);
        if (sessionIdStr == null) {
            return Optional.empty();
        }
        try {
            return getById(Long.parseLong(sessionIdStr));
        } catch (NumberFormatException e) {
            evictByJti(jti);
            return Optional.empty();
        }
    }

    /**
     * 写入或更新 Session 缓存；TTL 为 refresh token 剩余有效时间，过期则改为 evict。
     * 同时写入 id 快照与 jti 索引两个 Key。
     */
    public void cache(AuthSession session) {
        if (!enabled || session == null || session.getId() == null || session.getRefreshTokenJti() == null) {
            return;
        }
        if (session.getRefreshExpiresAt() == null || session.getAbsoluteExpiresAt() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        long refreshTtlSeconds = Duration.between(now, session.getRefreshExpiresAt()).getSeconds();
        long absoluteTtlSeconds = Duration.between(now, session.getAbsoluteExpiresAt()).getSeconds();
        long ttlSeconds = Math.min(refreshTtlSeconds, absoluteTtlSeconds);
        if (ttlSeconds <= 0) {
            evict(session);
            return;
        }
        AuthSessionCacheSnapshot snapshot = AuthSessionCacheSnapshot.from(session);
        Duration ttl = Duration.ofSeconds(ttlSeconds);
        redis.opsForValue().set(KEY_ID_PREFIX + session.getId(), serialize(snapshot), ttl);
        redis.opsForValue().set(KEY_JTI_PREFIX + session.getRefreshTokenJti(), String.valueOf(session.getId()), ttl);
    }

    /** 删除指定 Session 的 id 快照与 jti 索引。 */
    public void evict(AuthSession session) {
        if (session == null) {
            return;
        }
        evict(session.getId(), session.getRefreshTokenJti());
    }

    /** 按 sessionId 与 refreshTokenJti 分别删除对应 Redis Key（Rotation 时 jti 可能已变更）。 */
    public void evict(Long sessionId, String refreshTokenJti) {
        if (sessionId != null) {
            redis.delete(KEY_ID_PREFIX + sessionId);
        }
        evictByJti(refreshTokenJti);
    }

    /** 仅删除 jti 索引 Key，用于 refresh 轮换后清理旧 jti。 */
    public void evictByJti(String refreshTokenJti) {
        if (refreshTokenJti != null) {
            redis.delete(KEY_JTI_PREFIX + refreshTokenJti);
        }
    }

    /** 读取并反序列化快照；refresh 已过期或格式非法时删除脏 Key 并返回 empty。 */
    private Optional<AuthSessionCacheSnapshot> readSnapshot(String key) {
        String raw = redis.opsForValue().get(key);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            AuthSessionCacheSnapshot snapshot = deserialize(raw);
            if (snapshot.isRefreshExpired() || snapshot.isAbsoluteExpired()) {
                evict(snapshot.id(), snapshot.refreshTokenJti());
                return Optional.empty();
            }
            return Optional.of(snapshot);
        } catch (IllegalArgumentException e) {
            redis.delete(key);
            return Optional.empty();
        }
    }

    /** 将快照序列化为分隔符拼接的字符串，字段顺序与 {@link #deserialize} 一致。 */
    private String serialize(AuthSessionCacheSnapshot snapshot) {
        return String.join(String.valueOf(SEP),
                String.valueOf(snapshot.id()),
                String.valueOf(snapshot.userId()),
                nullToEmpty(snapshot.refreshTokenJti()),
                nullToEmpty(snapshot.refreshTokenHash()),
                String.valueOf(snapshot.refreshExpiresAtEpoch()),
                snapshot.revokedAtEpoch() == null ? "" : String.valueOf(snapshot.revokedAtEpoch()),
                String.valueOf(snapshot.rememberMe()),
                nullToEmpty(snapshot.loginIp()),
                String.valueOf(snapshot.absoluteExpiresAtEpoch())
        );
    }

    /** 反序列化 Redis 中的快照字符串；字段数不为 9 时抛出 {@link IllegalArgumentException}。 */
    private AuthSessionCacheSnapshot deserialize(String raw) {
        String[] parts = raw.split(String.valueOf(SEP), -1);
        if (parts.length != 9) {
            throw new IllegalArgumentException("invalid session cache payload");
        }
        Long revokedAt = parts[5].isEmpty() ? null : Long.parseLong(parts[5]);
        return new AuthSessionCacheSnapshot(
                Long.parseLong(parts[0]),
                Long.parseLong(parts[1]),
                emptyToNull(parts[2]),
                emptyToNull(parts[3]),
                Long.parseLong(parts[4]),
                revokedAt,
                Boolean.parseBoolean(parts[6]),
                emptyToNull(parts[7]),
                Long.parseLong(parts[8])
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
