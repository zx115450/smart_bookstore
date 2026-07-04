package com.zx.auth.service;

import com.zx.auth.config.AuthRateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 认证相关的 Redis 操作：验证码、发码/登录限流、Access Token 黑名单。
 */
@Service
@RequiredArgsConstructor
public class AuthRedisService {

    private static final String PREFIX_CODE = "auth:code:";
    private static final String PREFIX_LIMIT_SEND_TARGET_MIN = "auth:limit:send:target:min:";
    private static final String PREFIX_LIMIT_SEND_TARGET_HOUR = "auth:limit:send:target:hour:";
    private static final String PREFIX_LIMIT_SEND_IP_HOUR = "auth:limit:send:ip:";
    private static final String PREFIX_LIMIT_LOGIN_FAIL = "auth:limit:login:fail:";
    private static final String PREFIX_BLACKLIST_ACCESS = "auth:blacklist:access:";

    private final StringRedisTemplate redis;
    private final AuthRateLimitProperties rateLimitProperties;

    @Value("${auth.verification-code-ttl-seconds:300}")
    private long verificationCodeTtlSeconds;

    /**
     * 保存验证码哈希，Key 为 auth:code:{loginType}:{scene}:{target}，TTL 见 auth.verification-code-ttl-seconds。
     */
    public void saveVerificationCode(String loginType, String scene, String target, String codeHash) {
        String key = codeKey(loginType, scene, target);
        redis.opsForValue().set(key, codeHash, Duration.ofSeconds(verificationCodeTtlSeconds));
    }

    /**
     * 校验验证码哈希是否与 Redis 中一致；成功则删除 Key（一次性消费）。
     *
     * @return 校验通过返回 true，不存在或不匹配返回 false
     */
    public boolean verifyAndConsumeCode(String loginType, String scene, String target, String codeHash) {
        String key = codeKey(loginType, scene, target);
        String stored = redis.opsForValue().get(key);
        if (stored == null || !stored.equals(codeHash)) {
            return false;
        }
        redis.delete(key);
        return true;
    }

    /**
     * 发验证码前的限流检查：同一 target 分钟/小时上限、同一 IP 小时上限。
     *
     * @throws IllegalArgumentException 触发任一限流规则时
     */
    public void checkSendCodeRateLimit(String target, String clientIp) {
        if (!allow(PREFIX_LIMIT_SEND_TARGET_MIN + target, 1, rateLimitProperties.getSendCodePerTargetSeconds())) {
            throw new IllegalArgumentException("发送太频繁，请稍后再试");
        }
        if (!allow(PREFIX_LIMIT_SEND_TARGET_HOUR + target, rateLimitProperties.getSendCodePerTargetHourly(), 3600)) {
            throw new IllegalArgumentException("该账号发送次数过多，请稍后再试");
        }
        if (clientIp != null && !allow(PREFIX_LIMIT_SEND_IP_HOUR + clientIp, rateLimitProperties.getSendCodePerIpHourly(), 3600)) {
            throw new IllegalArgumentException("IP 发送次数过多，请稍后再试");
        }
    }

    /**
     * 登录前检查该账号/目标是否因连续失败被临时锁定。
     *
     * @param loginKey 密码登录为 account，验证码登录为 target
     * @throws IllegalArgumentException 失败次数达到 auth.rate-limit.login-fail-max 时
     */
    public void checkLoginAllowed(String loginKey) {
        if (loginKey == null) {
            return;
        }
        String key = PREFIX_LIMIT_LOGIN_FAIL + loginKey;
        String value = redis.opsForValue().get(key);
        if (value != null) {
            long fails = Long.parseLong(value);
            if (fails >= rateLimitProperties.getLoginFailMax()) {
                throw new IllegalArgumentException("登录失败次数过多，请"
                        + rateLimitProperties.getLoginFailLockMinutes() + "分钟后再试");
            }
        }
    }

    /**
     * 记录一次登录失败，首次失败时设置锁定窗口 TTL（login-fail-lock-minutes）。
     */
    public void recordLoginFailure(String loginKey) {
        if (loginKey == null) {
            return;
        }
        String key = PREFIX_LIMIT_LOGIN_FAIL + loginKey;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofMinutes(rateLimitProperties.getLoginFailLockMinutes()));
        }
    }

    /** 登录成功后清除该 loginKey 的失败计数。 */
    public void clearLoginFailure(String loginKey) {
        if (loginKey == null) {
            return;
        }
        redis.delete(PREFIX_LIMIT_LOGIN_FAIL + loginKey);
    }

    /**
     * 将 Access Token 的 jti 加入黑名单（logout 时调用），TTL 为 token 剩余有效时间。
     */
    public void blacklistAccessToken(String jti, long ttlSeconds) {
        if (jti == null || ttlSeconds <= 0) {
            return;
        }
        redis.opsForValue().set(PREFIX_BLACKLIST_ACCESS + jti, "1", Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 判断 Access Token 的 jti 是否在黑名单中。
     */
    public boolean isAccessBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey(PREFIX_BLACKLIST_ACCESS + jti));
    }

    /** 滑动窗口计数：窗口内次数不超过 maxCount 则允许，首次计数时设置过期时间。 */
    private boolean allow(String key, int maxCount, long windowSeconds) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        return count != null && count <= maxCount;
    }

    /** 组装验证码 Redis Key：auth:code:{loginType}:{scene}:{target} */
    private String codeKey(String loginType, String scene, String target) {
        return PREFIX_CODE + loginType + ":" + scene + ":" + target;
    }
}
