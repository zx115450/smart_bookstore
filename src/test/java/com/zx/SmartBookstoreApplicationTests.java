package com.zx;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.internal.operators.observable.BlockingObservableLatest;
import jakarta.annotation.Resource;
import okhttp3.Call;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.*;
import org.redisson.api.queue.QueueAddArgs;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 轻量级单测：不启动完整 Spring Boot 容器。
 * 含本机「分桶近似滑动窗口」热点计数示例（对应缓存 C5-B 思路）。
 */
@SpringBootTest
class SmartBookstoreApplicationTests {

    /** bookId → (bucketIndex → count) */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, Long>> map = new ConcurrentHashMap<>();

    private static final long WINDOW_MS = 60_000L;
    private static final long BUCKET_MS = 10_000L;
    private static final long THRESHOLD = 50L;

    private final StringRedisTemplate redisTemplate;

    @Autowired
    SmartBookstoreApplicationTests(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @FunctionalInterface
    private interface func {
        int apply(int a, int b, int c);
    }

    @Test
    void fnn  () throws IOException {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(factory);
        redisTemplate.afterPropertiesSet();
        String Key = "gay:zx";
        redisTemplate.opsForValue().setBit(Key , 0 ,  true );
        if (Boolean.TRUE.equals(redisTemplate.opsForValue().getBit(Key , 0 ))){
            System.out.println("gay");
        }
        byte[] execute = redisTemplate.execute((RedisCallback<byte[]>) (connect) -> {
            byte[] bytes = connect.get(Key.getBytes());
            return bytes;
            });
        if ((execute[0]&(1<<7)) != 0) {
            System.out.println("gay");
        }

        List<Long> list = redisTemplate.opsForValue().bitField(Key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(31))
                        .valueAt(0));


    }




    @Test
    void test_d() throws InterruptedException {
        Supplier<Config> supplier = () -> {
            Config config = new Config();
            config.useSingleServer().setAddress("redis://127.0.0.1:6379");
            return config;
        };
        RedissonClient redisson = Redisson.create(
                supplier.get()
        );
        String key = "gay:delay:lockDeque";
        RBlockingDeque<String> blockingDeque = redisson.getBlockingDeque(key);
        RDelayedQueue<String> delayedQueue = redisson.getDelayedQueue(blockingDeque);
        delayedQueue.offer("gay" , 1 , TimeUnit.SECONDS);
        String take = blockingDeque.take();
        System.out.println(take);
    }

    class zx {
        int name;
        int age;
        zx(int name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    @Test
    void test_d2() throws InterruptedException {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(factory);
        redisTemplate.afterPropertiesSet();

        ZSetOperations<String, String> ops = redisTemplate.opsForZSet();
        Set<ZSetOperations.@NonNull TypedTuple<String>> gay = ops.rangeWithScores("gay", 0, 9);
        for (ZSetOperations.TypedTuple tuple : gay) {
            String score = String.valueOf(tuple.getScore());
            Object value = tuple.getValue();

        }

    }

    @Test
    void test_d3() throws InterruptedException, SQLException {
        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/tlias?useSSL=false&serverTimezone=UTC" , "root" , "1234");
        PreparedStatement ps = connection.prepareStatement("select * from tlias.emp where id = ?");
        ps.setInt(1 , 7);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            String id = String.valueOf(rs.getInt("id"));
            String age = rs.getString("name");
            System.out.println(List.of(id , age));
        }
    }


    @Test
    void test_d4() throws InterruptedException {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(4);
        pool.setMaxPoolSize(8);
        pool.setQueueCapacity(1000_000);
        pool.setThreadNamePrefix("pool-");
        pool.setKeepAliveSeconds(60);
        pool.setAllowCoreThreadTimeOut(true);
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        pool.initialize();

        //自增id
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        //允许32位
        Long id = ops.increment("gay:increment");



    }


    @Test
    void test_d5() throws InterruptedException {

    }



    static class Parent {
        static {
            System.out.println("P static");
        }
        {
            System.out.println("P instance");
        }
        Parent() { System.out.println("P ctor"); }
    }
    private static long k = 100000;
    private static long sum = 0;
    private final ReentrantLock lock = new ReentrantLock();
//    private final Condition condition = lock.newCondition();

    class Producer implements Runnable {
        @Override
        public void run() {
            lock.lock();
            try {
                while (k > 0) {
                    k--;
                    sum ++;
                }
            } finally {
                lock.unlock();
            }
        }
    }
    @Test
    void testThread () throws Exception {

        ExecutorService pool = Executors.newFixedThreadPool(8);
        pool.execute(new Producer());
        pool.execute(new Producer());
        pool.execute(new Producer());
        pool.execute(new Producer());
        pool.execute(new Producer());
        pool.execute(new Producer());
        pool.execute(new Producer());
        pool.execute(new Producer());

        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);
        System.out.println(sum);
    }


    /**
     * 有界阻塞队列（学习版）。
     * <p>
     * 原写法的问题：
     * 1. ReentrantLock 必须配合 Condition.await/signal，不能用 Object.wait/notify
     * 2. put/get 各持一把锁时，ArrayDeque 会被并发读写（非线程安全）
     * 3. getFirst 只读不出队，应使用 removeFirst
     * 4. 锁不要用 static，否则所有队列实例共享同一把锁
     */
    static class BlockedQueue {
        private final ArrayDeque<Integer> queue;
        private final int maxSize;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();

        BlockedQueue(int maxSize) {
            if (maxSize <= 0) {
                throw new IllegalArgumentException("maxSize must be > 0");
            }
            this.maxSize = maxSize;
            this.queue = new ArrayDeque<>(maxSize);
        }

        void put(int x) throws InterruptedException {
            lock.lock();
            try {
                while (queue.size() == maxSize) {
                    notFull.await();
                }
                queue.addLast(x);
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        int get() throws InterruptedException {
            lock.lock();
            try {
                while (queue.isEmpty()) {
                    notEmpty.await();
                }
                int first = queue.removeFirst();
                notFull.signal();
                return first;
            } finally {
                lock.unlock();
            }
        }

        int size() {
            lock.lock();
            try {
                return queue.size();
            } finally {
                lock.unlock();
            }
        }
    }

    @Test
    void testThread2() throws Exception {
        int capacity = 8;
        int total = 200;
        BlockedQueue queue = new BlockedQueue(capacity);

        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch producersDone = new CountDownLatch(2);
        CountDownLatch consumersDone = new CountDownLatch(2);
        List<Integer> consumed = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger putErrors = new AtomicInteger();
        AtomicInteger getErrors = new AtomicInteger();

        // 两个生产者：合计放入 0..199
        pool.execute(() -> produceRange(queue, 0, total / 2, producersDone, putErrors));
        pool.execute(() -> produceRange(queue, total / 2, total, producersDone, putErrors));

        // 两个消费者：合计取 total 次
        int eachTake = total / 2;
        pool.execute(() -> consumeTimes(queue, eachTake, consumed, consumersDone, getErrors));
        pool.execute(() -> consumeTimes(queue, eachTake, consumed, consumersDone, getErrors));

        assertTrue(producersDone.await(10, TimeUnit.SECONDS), "producers timeout");
        assertTrue(consumersDone.await(10, TimeUnit.SECONDS), "consumers timeout");
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(0, putErrors.get());
        assertEquals(0, getErrors.get());
        assertEquals(0, queue.size());
        assertEquals(total, consumed.size());

        List<Integer> sorted = new ArrayList<>(consumed);
        Collections.sort(sorted);
        for (int i = 0; i < total; i++) {
            assertEquals(i, sorted.get(i));
        }
    }

    private static void produceRange(BlockedQueue queue, int from, int to,
                                     CountDownLatch done, AtomicInteger errors) {
        try {
            for (int i = from; i < to; i++) {
                queue.put(i);
            }
        } catch (Exception e) {
            errors.incrementAndGet();
        } finally {
            done.countDown();
        }
    }

    private static void consumeTimes(BlockedQueue queue, int times, List<Integer> consumed,
                                     CountDownLatch done, AtomicInteger errors) {
        try {
            for (int i = 0; i < times; i++) {
                consumed.add(queue.get());
            }
        } catch (Exception e) {
            errors.incrementAndGet();
        } finally {
            done.countDown();
        }
    }

    @Test
    void contextLoads() {
        func f = (a , b , c) -> {
            return a + b + c;
        };
        int x = f.apply(1 , 2 , 3);
        System.out.println(x);
    }
    @Test
    void bucketSlidingWindow_shouldDropExpiredAndSumAlive() {
        long id = 1L;
        long now = System.currentTimeMillis();
        long currentBucket = now / BUCKET_MS;
        long minBucket = currentBucket - (WINDOW_MS / BUCKET_MS) + 1;

        ConcurrentHashMap<Long, Long> tong =
                map.computeIfAbsent(id, k -> new ConcurrentHashMap<>());

        tong.put(minBucket - 1, 100L);

        tong.put(currentBucket - 1, 20L);
        tong.merge(currentBucket, 35L, Long::sum);

        tong.entrySet().removeIf(e -> e.getKey() < minBucket);

        long sum = 0L;
        for (Map.Entry<Long, Long> entry : tong.entrySet()) {
            sum += entry.getValue();
        }

        assertFalse(tong.containsKey(minBucket - 1));
        assertEquals(55L, sum);
        assertTrue(sum >= THRESHOLD);
    }

    @Test
    void recordAccess_shouldIncrementCurrentBucket() {
        long id = 2L;
        recordAccess(id);
        recordAccess(id);
        recordAccess(id);

        long nowBucket = System.currentTimeMillis() / BUCKET_MS;
        assertEquals(3L, map.get(id).get(nowBucket));
        assertTrue(sumInWindow(id) >= 3L);
        assertFalse(isHot(id)); // 未达 threshold=50
    }

    private void recordAccess(long bookId) {
        long nowBucket = System.currentTimeMillis() / BUCKET_MS;
        long minBucket = nowBucket - (WINDOW_MS / BUCKET_MS) + 1;
        ConcurrentHashMap<Long, Long> tong =
                map.computeIfAbsent(bookId, k -> new ConcurrentHashMap<>());
        tong.merge(nowBucket, 1L, Long::sum);
        tong.entrySet().removeIf(e -> e.getKey() < minBucket);
    }

    private long sumInWindow(long bookId) {
        ConcurrentHashMap<Long, Long> tong = map.get(bookId);
        if (tong == null) {
            return 0L;
        }
        long nowBucket = System.currentTimeMillis() / BUCKET_MS;
        long minBucket = nowBucket - (WINDOW_MS / BUCKET_MS) + 1;
        tong.entrySet().removeIf(e -> e.getKey() < minBucket);
        return tong.values().stream().mapToLong(Long::longValue).sum();
    }

    private boolean isHot(long bookId) {
        return sumInWindow(bookId) >= THRESHOLD;
    }
}
