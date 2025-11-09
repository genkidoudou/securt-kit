package io.github.hexlodev.config;

import io.github.hexlodev.core.config.FieldEncryptorProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 自动配置类
 *
 * @author luyanan
 * @since 2025/11/3
 */
@Configuration
@EnableConfigurationProperties(FieldEncryptorProperties.class)
public class SecurtKitAutoConfiguration {


}
