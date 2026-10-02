package com.zx.media.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 按配置切换 Mock / 真实 HTTP / 禁用占位 Client。
 */
@Configuration
@EnableConfigurationProperties({LiteMediaProperties.class, BookstoreMediaProperties.class})
public class LiteMediaClientConfiguration {

    @Bean
    @ConditionalOnMissingBean(LiteMediaClient.class)
    LiteMediaClient liteMediaClient(LiteMediaProperties properties) {
        if (!properties.isEnabled()) {
            return new DisabledLiteMediaClient();
        }
        if (properties.isMock()) {
            return new MockLiteMediaClient();
        }
        return new LiteMediaClientImpl(liteVodRestClient(properties), properties);
    }

    private static RestClient liteVodRestClient(LiteMediaProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1, properties.getConnectTimeoutMs())))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(Math.max(1, properties.getReadTimeoutMs())));

        String baseUrl = properties.getBaseUrl();
        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return RestClient.builder()
                .baseUrl(baseUrl == null ? "http://127.0.0.1:8080" : baseUrl)
                .requestFactory(factory)
                .build();
    }
}
