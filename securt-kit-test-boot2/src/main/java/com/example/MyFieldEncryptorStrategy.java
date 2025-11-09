package com.example;

import org.springframework.stereotype.Component;
import  io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
@Component
public class MyFieldEncryptorStrategy implements FieldEncryptorStrategy {


    private static final String prefix = "(加密)";

    @Override
    public String encryption(String oldValue) {
        return oldValue + prefix;
    }

    @Override
    public String decryption(String oldValue) {
        return oldValue.replace(prefix, "");
    }
}
