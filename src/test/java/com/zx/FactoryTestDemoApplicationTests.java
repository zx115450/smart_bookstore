package com.zx;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

//@SpringBootTest
class FactoryTestDemoApplicationTests {

    @Test
    void contextLoads() {
       long a = 1L;
       long b= 1000000000L;
       long [] map = new long[10];
       long  s = System.currentTimeMillis();
       for (long i=a;i<=b;i++) {
           long x = i;
           while (x>0) {
                map[Math.toIntExact(x % 10)]++;
                x/=10;
            }
       }
       long e = System.currentTimeMillis();
        System.out.println(e - s);
    }

}
