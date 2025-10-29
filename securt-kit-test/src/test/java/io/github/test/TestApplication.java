package io.github.test;

import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@SpringBootConfiguration
@ComponentScan(basePackages = {
        "io.github",
        "cn.hutool.extra.spring"
})
public class TestApplication {

    @Bean
    public FieldEncryptorStrategy defaultFieldEncryptorStrategy() {
        return new FieldEncryptorStrategy() {
            @Override
            public String encryption(String oldValue) {
                return oldValue;
            }

            @Override
            public String decryption(String oldValue) {
                return oldValue;
            }
        };
    }
}
