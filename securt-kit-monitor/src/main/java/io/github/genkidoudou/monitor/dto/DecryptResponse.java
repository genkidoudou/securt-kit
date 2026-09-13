package io.github.genkidoudou.monitor.dto;

import lombok.Data;

/**
 * 解密响应
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
public class DecryptResponse {
    private String encrypted;
    private String decrypted;
    private String strategy;
}

