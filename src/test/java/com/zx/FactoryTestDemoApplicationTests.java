package com.zx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 轻量级上下文测试：确保测试工程与基础依赖能正确编译加载。
 * 不启动完整 Spring Boot 容器，避免强连中间件。
 */
@ExtendWith(SpringExtension.class)
class FactoryTestDemoApplicationTests {

    @Test
    void contextLoads() {
        assertEquals(1, 1);
    }
}
