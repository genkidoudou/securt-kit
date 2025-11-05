package io.github.hexlodev.ui.monitor.dto;

import lombok.Data;

/**
 * SQL 解析请求
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
public class ParseSqlRequest {
    /**
     * 要解析的 SQL 语句
     */
    private String sql;
}

