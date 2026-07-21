package com.zx;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

//@SpringBootTest
class FactoryTestDemoApplicationTests {

    @Test
    void contextLoads() {
        List<Integer> list = List.of(1 , 2 , 3, 4, 5, 56);
        AtomicBoolean is = new AtomicBoolean(false);
        list.forEach(i -> {
            if (i == 1) {
                is.set(true);
            }
        });
        System.out.println(is);
    }

}
