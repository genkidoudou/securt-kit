package io.github.hexlodev.ui.monitor.dto;

import lombok.Data;

import java.util.List;

/**
 * 数据初始化响应
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
public class DataInitResponse {
    /**
     * 处理的记录数
     */
    private Integer processedCount;

    /**
     * 处理的字段数
     */
    private Integer processedFieldCount;

    /**
     * 生成的SQL语句列表
     */
    private List<String> sqlStatements;

    /**
     * SQL 总条数
     */
    private Integer totalSqlCount;

    /**
     * 操作类型（encrypt/decrypt）
     */
    private String operationType;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 主键字段名
     */
    private String primaryKeyField;
}

