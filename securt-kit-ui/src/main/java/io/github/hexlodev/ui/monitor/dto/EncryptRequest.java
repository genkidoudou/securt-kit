package io.github.hexlodev.ui.monitor.dto;

import lombok.Data;

/**
 * 加密请求
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
public class EncryptRequest {
    /**
     * 要加密的文本（必填）
     */
    private String text;

    /**
     * 表名（可选，用于获取策略）
     */
    private String tableName;

    /**
     * 字段名（可选，用于获取策略）
     */
    private String fieldName;

    /**
     * 策略类名（可选，优先级最高）
     */
    private String strategy;
}

