package com.example;

import cn.hutool.extra.spring.SpringUtil;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot 测试应用主类
 *
 * @author hexlodev
 * @since 1.0.0
 */
@SpringBootApplication(exclude = {JmxAutoConfiguration.class})
@MapperScan(basePackages = {"com.example.mapper"})
@Import(SpringUtil.class)
public class TestApplication3 {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication3.class, args);
    }
}


