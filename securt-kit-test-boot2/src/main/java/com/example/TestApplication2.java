package com.example;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 测试应用主类
 *
 * @author hexlodev
 * @since 1.0.0
 */
@SpringBootApplication
@MapperScan(basePackages = {"com.example.mapper"})

public class TestApplication2 {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication2.class, args);
    }
}


