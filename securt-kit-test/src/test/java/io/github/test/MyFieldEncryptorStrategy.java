package io.github.test;

import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;

public class MyFieldEncryptorStrategy implements FieldEncryptorStrategy {


    private static final String prefix = "$$$$$--";

    @Override
    public String encryption(String oldValue) {
        return prefix + oldValue;
    }

    @Override
    public String decryption(String oldValue) {
        return oldValue.replace(prefix, "");
    }
}
