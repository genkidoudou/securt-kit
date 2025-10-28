package io.github.hexlodev.core.parser.dto;

import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段加密信息
 * - columnName: 查询结果中的列名/别名
 * - sourceColumn/sourceTableName: 源表与源列
 * - fieldEncryptor: 该字段对应的加密策略类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldEncryptorInfoDto {

    /**
     * 列名（查询结果中的字段名或别名）
     */
    private String columnName;

    /**
     * 源列名（实际数据库表中的字段名）
     */
    private String sourceColumn;

    /**
     * 源表名
     */
    private String sourceTableName;

    /**
     * 字段加密注解
     */
    private Class<? extends FieldEncryptorStrategy> fieldEncryptor;
}
