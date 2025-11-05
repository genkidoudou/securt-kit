package io.github.hexlodev.ui.monitor.dto;

import io.github.hexlodev.ui.monitor.security.SafeInput;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * SQL 加密请求
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
public class EncryptSqlRequest {
    /**
     * 要加密的 SQL 语句（包含实际值）
     */
    @NotNull(message = "SQL 语句不能为空")
    @SafeInput(maxLength = 50 * 1024, message = "SQL 语句过长")
    private String sql;
}

