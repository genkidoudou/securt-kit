package io.github.test;

import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurtkitUtilsAutoConfiguration {


    @Bean
    public FieldEncryptorStrategy fieldEncryptorStrategy() {
        return new MyFieldEncryptorStrategy();
    }
}
