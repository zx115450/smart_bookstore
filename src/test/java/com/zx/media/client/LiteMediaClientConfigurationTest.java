package com.zx.media.client;

import com.zx.reader.ReaderException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LiteMediaClientConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LiteMediaClientConfiguration.class);

    @Test
    void shouldRegisterMockClient_whenMockTrue() {
        runner.withPropertyValues(
                "bookstore.media.lite-vod.enabled=true",
                "bookstore.media.lite-vod.mock=true"
        ).run(context -> {
            assertThat(context).hasSingleBean(LiteMediaClient.class);
            assertThat(context.getBean(LiteMediaClient.class)).isInstanceOf(MockLiteMediaClient.class);
            assertThat(context.getBean(LiteMediaClient.class).listChapters("mock-doc-1").chapters()).hasSize(3);
        });
    }

    @Test
    void shouldRegisterDisabledClient_whenEnabledFalse() {
        runner.withPropertyValues(
                "bookstore.media.lite-vod.enabled=false",
                "bookstore.media.lite-vod.mock=true"
        ).run(context -> {
            assertThat(context).hasSingleBean(LiteMediaClient.class);
            LiteMediaClient client = context.getBean(LiteMediaClient.class);
            assertThat(client).isInstanceOf(DisabledLiteMediaClient.class);
            ReaderException ex = assertThrows(ReaderException.class, () -> client.getMedia("x"));
            assertThat(ex.getCode()).isEqualTo(6002);
        });
    }

    @Test
    void shouldRegisterHttpClient_whenMockFalse() {
        runner.withPropertyValues(
                "bookstore.media.lite-vod.enabled=true",
                "bookstore.media.lite-vod.mock=false",
                "bookstore.media.lite-vod.base-url=http://127.0.0.1:8080",
                "bookstore.media.lite-vod.internal-token=tok"
        ).run(context -> {
            assertThat(context).hasSingleBean(LiteMediaClient.class);
            assertThat(context.getBean(LiteMediaClient.class)).isInstanceOf(LiteMediaClientImpl.class);
        });
    }
}
