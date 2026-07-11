package com.zx;

import com.zx.auth.config.AuthJwtProperties;
import com.zx.auth.config.AuthQqOAuthProperties;
import com.zx.auth.config.AuthRateLimitProperties;
import com.zx.bookstore.borrow.config.BookstoreBorrowOverdueProperties;
import com.zx.bookstore.config.BookstoreCacheProperties;
import com.zx.bookstore.trade.config.BookstoreTradeProperties;
import com.zx.marketing.checkin.config.CheckinProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        AuthJwtProperties.class,
        AuthRateLimitProperties.class,
        AuthQqOAuthProperties.class,
        BookstoreCacheProperties.class,
        BookstoreBorrowOverdueProperties.class,
        CheckinProperties.class,
        BookstoreTradeProperties.class
})
public class FactoryTestDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(FactoryTestDemoApplication.class, args);
        log.info("server started successfully");
    }

}
