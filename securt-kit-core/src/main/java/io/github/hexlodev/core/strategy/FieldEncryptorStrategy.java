package io.github.hexlodev.core.strategy;

/**
 * 加解密策略
 * @author luyanan
 * @since 2025/10/7
 */
public interface FieldEncryptorStrategy {


    /**
     * 加密
     * @since 2025/10/7
     * @param oldValue  原始的值
     * @return 加密之后的值
     */
    String encryption(String oldValue);


    /**
     * 解密
     * @since 2025/10/7
     * @param oldValue 原始的值
     * @return 解密之后的值
     */
    String decryption(String oldValue);
}
