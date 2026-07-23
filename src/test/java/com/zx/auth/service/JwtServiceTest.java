package com.zx.auth.service;

import com.zx.auth.config.AuthJwtProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * JwtService 的单元测试：验证 Access Token 签发、解析与错误签名分支。
 * <p>
 * <b>为什么用 {@code @ExtendWith(MockitoExtension.class)}？</b>
 * 这是 JUnit 5 与 Mockito 的集成扩展，它会自动初始化 {@code @Mock} 和 {@code @InjectMocks} 字段，
 * 但<strong>不启动 Spring 容器</strong>，因此测试只聚焦 JWT 算法本身，不依赖配置中心的实际 YAML。
 * <p>
 * <b>依赖说明：</b>
 * <ul>
 *   <li>{@link AuthJwtProperties}：配置属性对象，用 {@code @Mock} 替换，避免读取真实配置文件。</li>
 *   <li>{@link JwtService}：被测对象，通过 {@code @InjectMocks} 把 Mock 配置注入其中。</li>
 *   <li>{@code when(...).thenReturn(...)}：Mockito 打桩，用于控制 {@code secret}、{@code issuer}、
 *       {@code accessExpireSeconds} 等配置值。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private AuthJwtProperties jwtProperties;

    @InjectMocks
    private JwtService jwtService;

    @Test
    void shouldCreateAndParseAccessToken() {
        when(jwtProperties.getSecret()).thenReturn("TestSecretKeyAtLeast256BitsForHmac256Algorithm");
        when(jwtProperties.getIssuer()).thenReturn("Factory_Test_Demo");
        when(jwtProperties.getAccessExpireSeconds()).thenReturn(7200L);

        String token = jwtService.createAccessToken(1L, "user", 100L, List.of("USER"));
        assertNotNull(token);

        JwtService.AccessTokenClaims claims = jwtService.parseAccessToken(token);
        assertEquals(1L, claims.userId());
        assertEquals("user", claims.username());
        assertEquals(100L, claims.sessionId());
        assertTrue(claims.roles().contains("USER"));
    }

    @Test
    void shouldThrowOnInvalidToken() {
        when(jwtProperties.getSecret()).thenReturn("TestSecretKeyAtLeast256BitsForHmac256Algorithm");
        when(jwtProperties.getIssuer()).thenReturn("Factory_Test_Demo");

        assertThrows(JwtService.InvalidAccessTokenException.class,
                () -> jwtService.parseAccessToken("not-a-token"));
    }
}
