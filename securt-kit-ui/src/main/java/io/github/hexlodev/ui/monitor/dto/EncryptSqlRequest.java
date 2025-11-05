package io.github.hexlodev.ui.monitor.dto;

import lombok.Data;

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
    private String sql;
}

