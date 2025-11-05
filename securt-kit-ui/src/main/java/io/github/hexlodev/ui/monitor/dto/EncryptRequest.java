package io.github.hexlodev.ui.monitor.dto;

import io.github.hexlodev.ui.monitor.security.SafeInput;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

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
    @NotNull(message = "文本内容不能为空")
    @SafeInput(maxLength = 10 * 1024, message = "文本内容过长")
    private String text;

    /**
     * 表名（可选，用于获取策略）
     */
    @SafeInput(maxLength = 64, pattern = "^[a-zA-Z_][a-zA-Z0-9_]*$", message = "表名格式不正确")
    private String tableName;

    /**
     * 字段名（可选，用于获取策略）
     */
    @SafeInput(maxLength = 64, pattern = "^[a-zA-Z_][a-zA-Z0-9_]*$", message = "字段名格式不正确")
    private String fieldName;

    /**
     * 策略类名（可选，优先级最高）
     */
    @SafeInput(maxLength = 200, pattern = "^[a-zA-Z][a-zA-Z0-9_.]*$", message = "策略类名格式不正确")
    private String strategy;
}

