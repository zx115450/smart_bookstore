package com.zx.bookstore.catalog.service;

import com.zx.bookstore.catalog.support.BloomFilterSpec;
import com.zx.bookstore.catalog.support.RedisBloomHash;
import com.zx.bookstore.config.BookstoreCacheProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * 图书 ID 布隆过滤器（Redis Bitmap 实现），用于用户端详情查询防缓存穿透。
 * <p>
 * <b>Redis Key</b>：{@value #BLOOM_KEY}（String 类型，按位存储）。<br>
 * <b>语义</b>：{@link #mightContain} 返回 {@code false} → 一定不存在；
 * 返回 {@code true} → 可能存在（含假阳性，需继续查缓存/DB）。<br>
 * <b>限制</b>：经典布隆不支持删除；下架书的 id 仍会占 bit，仅多查一次 DB。
 * <p>
 * 配置见 {@code bookstore.cache.bloom-*}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookBloomRedisService {

    /** 全站共用一份位图，多实例共享。 */
    static final String BLOOM_KEY = "book:bloom:ids";

    private final StringRedisTemplate redis;
    private final BookstoreCacheProperties cacheProperties;

    /** 启动时由 n、p 计算出的 m、k，全局复用。 */
    private BloomFilterSpec spec;

    @PostConstruct
    void initSpec() {
        spec = BloomFilterSpec.of(
                cacheProperties.getBloomExpectedElements(),
                cacheProperties.getBloomFalsePositiveRate()
        );
        log.info("book bloom filter initialized, bitSize={}, hashFunctions={}, fpp={}",
                spec.bitSize(), spec.hashFunctions(), cacheProperties.getBloomFalsePositiveRate());
    }

    public boolean isEnabled() {
        return cacheProperties.isBloomEnabled();
    }

    public boolean shouldWarmupOnStartup() {
        return cacheProperties.isBloomWarmupOnStartup();
    }

    /**
     * 将 bookId 加入过滤器：对 k 个 offset 执行 {@code SETBIT 1}。
     * 新书 {@code createBook} 成功后调用；全量 {@link #rebuild} 时也会调用。
     */
    public void add(Long bookId) {
        if (!isEnabled() || bookId == null) {
            return;
        }
        try {
            for (int i = 0; i < spec.hashFunctions(); i++) {
                long offset = RedisBloomHash.offset(bookId, i, spec.bitSize());
                redis.opsForValue().setBit(BLOOM_KEY, offset, true);
            }
        } catch (Exception e) {
            log.warn("book bloom add failed, id={}, err={}", bookId, e.getMessage());
        }
    }

    /**
     * 判断 bookId 是否「可能存在」于已入库图书集合。
     *
     * @return {@code false} 任一对 k 个 bit 为 0 → <b>一定不存在</b>（无假阴性）；
     *         {@code true} 全为 1 → <b>可能存在</b>（含假阳性）；
     *         关闭布隆或 Redis 异常时 fail-open 返回 {@code true}，避免误伤正常请求。
     */
    public boolean mightContain(Long bookId) {
        if (!isEnabled() || bookId == null) {
            return true;
        }
        try {
            for (int i = 0; i < spec.hashFunctions(); i++) {
                long offset = RedisBloomHash.offset(bookId, i, spec.bitSize());
                Boolean bit = redis.opsForValue().getBit(BLOOM_KEY, offset);
                if (!Boolean.TRUE.equals(bit)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            // fail-open：Redis 不可用时放行查 DB，保证可用性优先
            log.warn("book bloom check failed, id={}, fail-open to DB, err={}", bookId, e.getMessage());
            return true;
        }
    }

    /**
     * 清空并重建过滤器（启动预热、运维补偿）。
     * 先 {@code DEL} 位图，再对每个 bookId 调用 {@link #add}。
     */
    public void rebuild(Collection<Long> bookIds) {
        if (!isEnabled()) {
            return;
        }
        try {
            redis.delete(BLOOM_KEY);
            if (bookIds == null || bookIds.isEmpty()) {
                log.info("book bloom rebuild finished, count=0");
                return;
            }
            int count = 0;
            for (Long bookId : bookIds) {
                if (bookId != null) {
                    add(bookId);
                    count++;
                }
            }
            log.info("book bloom rebuild finished, count={}", count);
        } catch (Exception e) {
            log.warn("book bloom rebuild failed, err={}", e.getMessage());
        }
    }
}
