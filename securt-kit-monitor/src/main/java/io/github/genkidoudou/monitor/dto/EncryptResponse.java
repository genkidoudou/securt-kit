package io.github.genkidoudou.monitor.dto;

import lombok.Data;

/**
 * 加密响应
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
public class EncryptResponse {
    private String original;
    private String encrypted;
    private String strategy;
}

