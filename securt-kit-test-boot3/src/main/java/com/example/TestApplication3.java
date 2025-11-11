package com.example;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;

/**
 * Spring Boot 测试应用主类
 *
 * @author hexlodev
 * @since 1.0.0
 */
@SpringBootApplication(exclude = {JmxAutoConfiguration.class})
@MapperScan(basePackages = {"com.example.mapper"})
public class TestApplication3 {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication3.class, args);
    }
}


