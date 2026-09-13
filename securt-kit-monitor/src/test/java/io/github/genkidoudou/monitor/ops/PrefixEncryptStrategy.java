package io.github.genkidoudou.monitor.ops;

import io.github.genkidoudou.core.strategy.FieldEncryptorStrategy;

/** 可逆前缀策略，仅用于 Monitor 单元测试 */
public final class PrefixEncryptStrategy implements FieldEncryptorStrategy {
    @Override
    public String encryption(String oldValue) {
        return oldValue == null ? null : "ENC:" + oldValue;
    }

    @Override
    public String decryption(String oldValue) {
        if (oldValue == null) {
            return null;
        }
        return oldValue.startsWith("ENC:") ? oldValue.substring(4) : oldValue;
    }
}
