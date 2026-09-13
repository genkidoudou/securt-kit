package io.github.genkidoudou.monitor.dto;

import lombok.Data;

/**
 * SQL 加密响应
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
public class EncryptSqlResponse {
    /**
     * 原始 SQL
     */
    private String originalSql;

    /**
     * 加密后的 SQL
     */
    private String encryptedSql;

    /**
     * 加密的字段数量
     */
    private Integer encryptedFieldCount;
}

