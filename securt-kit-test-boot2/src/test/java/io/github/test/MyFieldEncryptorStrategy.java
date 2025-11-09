package io.github.test;

import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;

public class MyFieldEncryptorStrategy implements FieldEncryptorStrategy {


    private static final String prefix = "(加密)";

    @Override
    public String encryption(String oldValue) {
        return oldValue + prefix;
    }

    @Override
    public String decryption(String oldValue) {
        return oldValue.replace(prefix, "") ;
    }
}
