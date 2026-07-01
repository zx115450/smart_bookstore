package com.zx;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
@MapperScan(value = "com.zx.auth.mapper", sqlSessionTemplateRef = "sqlSessionTemplate")
public class FactoryTestDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(FactoryTestDemoApplication.class, args);
        log.info("server started successfully");
    }

}
