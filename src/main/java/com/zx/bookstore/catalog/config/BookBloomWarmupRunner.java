package com.zx.bookstore.catalog.config;

import com.zx.bookstore.catalog.repository.BookRepository;
import com.zx.bookstore.catalog.service.BookBloomRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 应用启动后从 MySQL 全量预热图书 ID 布隆过滤器。
 * <p>
 * 解决：Redis 重启后位图为空、或历史书未增量 {@code add} 导致误判「不存在」。
 * 增量路径：{@code createBook} 成功后 {@link BookBloomRedisService#add}。
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "bookstore.cache", name = "bloom-warmup-on-startup", havingValue = "true", matchIfMissing = true)
public class BookBloomWarmupRunner implements ApplicationRunner {

    private final BookBloomRedisService bookBloomRedisService;
    private final BookRepository bookRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (!bookBloomRedisService.isEnabled() || !bookBloomRedisService.shouldWarmupOnStartup()) {
            return;
        }
        log.info("book bloom warmup started");
        bookBloomRedisService.rebuild(bookRepository.listAllIds());
    }
}
