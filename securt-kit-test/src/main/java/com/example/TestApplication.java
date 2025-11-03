package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot 测试应用主类
 * 禁用 DataSource 自动配置，使用手动配置
 *
 * @author hexlodev
 * @since 1.0.0
 */
@SpringBootApplication()
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
