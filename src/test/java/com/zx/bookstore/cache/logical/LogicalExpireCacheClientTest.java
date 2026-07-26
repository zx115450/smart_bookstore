package com.zx.bookstore.cache.logical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogicalExpireCacheClientTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private LogicalExpireCacheClient client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        client = new LogicalExpireCacheClient(redisTemplate, objectMapper);
    }

    @Test
    void miss_withLock_shouldLoadDbAndWriteLogicalPayload() throws Exception {
        when(valueOps.get("book:logical:1")).thenReturn(null);
        when(valueOps.setIfAbsent(eq("book:lock:1"), eq("1"), any(Duration.class))).thenReturn(true);

        Sample loaded = client.queryWithLogicalExpire(
                "book:logical:", "book:lock:", 1L, Sample.class,
                Duration.ofMinutes(30), id -> new Sample(id, "java"));

        assertEquals(1L, loaded.id());
        assertEquals("java", loaded.title());

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("book:logical:1"), jsonCaptor.capture(), any(Duration.class));
        RedisLogicalData payload = objectMapper.readValue(jsonCaptor.getValue(), RedisLogicalData.class);
        assertTrue(!payload.isAbsent());
        assertEquals("java", payload.getData().get("title").asText());
        verify(redisTemplate).delete("book:lock:1");
    }

    @Test
    void miss_dbNull_shouldWriteAbsentAndThrow() {
        when(valueOps.get("book:logical:9")).thenReturn(null);
        when(valueOps.setIfAbsent(eq("book:lock:9"), eq("1"), any(Duration.class))).thenReturn(true);

        assertThrows(LogicalExpireCacheClient.CacheMissException.class, () ->
                client.queryWithLogicalExpire(
                        "book:logical:", "book:lock:", 9L, Sample.class,
                        Duration.ofMinutes(30), id -> null));

        verify(valueOps).set(eq("book:logical:9"), anyString(), eq(Duration.ofMinutes(2)));
    }

    @Test
    void hit_notExpired_shouldReturnWithoutDb() throws Exception {
        RedisLogicalData payload = new RedisLogicalData(
                java.time.LocalDateTime.now().plusMinutes(10),
                objectMapper.valueToTree(new Sample(2L, "cached")),
                false);
        when(valueOps.get("book:logical:2")).thenReturn(objectMapper.writeValueAsString(payload));

        AtomicInteger dbCalls = new AtomicInteger();
        Sample result = client.queryWithLogicalExpire(
                "book:logical:", "book:lock:", 2L, Sample.class,
                Duration.ofMinutes(30), id -> {
                    dbCalls.incrementAndGet();
                    return new Sample(id, "db");
                });

        assertEquals("cached", result.title());
        assertEquals(0, dbCalls.get());
    }

    @Test
    void hit_expired_lockFail_shouldReturnStaleWithoutWaitingRebuild() throws Exception {
        RedisLogicalData payload = new RedisLogicalData(
                java.time.LocalDateTime.now().minusMinutes(1),
                objectMapper.valueToTree(new Sample(3L, "stale")),
                false);
        when(valueOps.get("book:logical:3")).thenReturn(objectMapper.writeValueAsString(payload));
        when(valueOps.setIfAbsent(eq("book:lock:3"), eq("1"), any(Duration.class))).thenReturn(false);

        AtomicInteger dbCalls = new AtomicInteger();
        Sample result = client.queryWithLogicalExpire(
                "book:logical:", "book:lock:", 3L, Sample.class,
                Duration.ofMinutes(30), id -> {
                    dbCalls.incrementAndGet();
                    return new Sample(id, "fresh");
                });

        assertEquals("stale", result.title());
        assertEquals(0, dbCalls.get());
    }

    @Test
    void saveAbsent_shouldSetPhysicalTtl() {
        client.saveAbsent("book:logical:0", Duration.ofMinutes(2));
        verify(valueOps, atLeastOnce()).set(eq("book:logical:0"), anyString(), eq(Duration.ofMinutes(2)));
    }

    record Sample(Long id, String title) {
    }
}
