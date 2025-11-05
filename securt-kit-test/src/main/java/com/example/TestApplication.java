package com.example;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot 测试应用主类
 *
 * @author hexlodev
 * @since 1.0.0
 */
@SpringBootApplication
@MapperScan(basePackages = {"com.example.mapper"})
@ComponentScan(basePackages = {
        "com.example",
        "io.github.hexlodev"  // 扫描 securt-kit 相关包，包括 UI 模块
})
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
