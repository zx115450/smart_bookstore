package com.zx.bookstore.seckill.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 秒杀 Lua 并发集成测：固定库存 N，并发请求 &gt; N，断言抢中数 ≤ N 且一人一单。
 * <p>
 * 需要本机 Docker。无 Docker 时本类会跳过（{@code disabledWithoutDocker}）。
 */
@Testcontainers(disabledWithoutDocker = true)
class SeckillRedisGrabIT {

    private static final long ACTIVITY_ID = 9001L;
    private static final int STOCK = 20;
    private static final int CONCURRENT_USERS = 80;

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    private SeckillRedisService seckillRedisService;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();

        seckillRedisService = new SeckillRedisService(template);
        seckillRedisService.initScript();
        seckillRedisService.warmUpStock(ACTIVITY_ID, STOCK, LocalDateTime.now().plusHours(2));
    }

    @Test
    void concurrentGrab_shouldNotOversell_andOneSuccessPerUser() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger already = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (long userId = 1; userId <= CONCURRENT_USERS; userId++) {
            final long uid = userId;
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    Long code = seckillRedisService.grab(ACTIVITY_ID, uid);
                    if (code == 1L) {
                        success.incrementAndGet();
                    } else if (code == 0L) {
                        soldOut.incrementAndGet();
                    } else if (code == 2L) {
                        already.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertEquals(STOCK, success.get(), "抢中数不得超过库存");
        assertEquals(CONCURRENT_USERS, success.get() + soldOut.get() + already.get());
        assertEquals(0, already.get(), "每人只抢一次，不应出现已参与");

        Integer remaining = seckillRedisService.getRemainingStock(ACTIVITY_ID);
        assertEquals(0, remaining);

        AtomicInteger secondSuccess = new AtomicInteger();
        for (long userId = 1; userId <= CONCURRENT_USERS; userId++) {
            Long code = seckillRedisService.grab(ACTIVITY_ID, userId);
            if (code == 1L) {
                secondSuccess.incrementAndGet();
            }
        }
        assertEquals(0, secondSuccess.get(), "库存耗尽后不应再抢中");
    }
}
