package io.github.genkidoudou.monitor.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * SQL 解析响应
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
public class ParseSqlResponse {
    /**
     * 占位符到表字段的映射
     * key: 占位符名称（如 SECURT_KIT_PLACEHOLDER_1）
     * value: 表字段信息
     */
    private Map<String, PlaceholderInfo> placeholderMap;

    /**
     * 需要加密的字段信息列表
     */
    private List<EncryptFieldInfo> encryptFields;

    /**
     * 占位符信息
     */
    @Data
    public static class PlaceholderInfo {
        /**
         * 占位符索引（从1开始）
         */
        private Integer index;

        /**
         * 表别名
         */
        private String tableAliasName;

        /**
         * 真实表名
         */
        private String sourceTableName;

        /**
         * 真实字段名
         */
        private String sourceColumn;

        /**
         * 是否来自真实表
         */
        private Boolean fromSourceTable;

        /**
         * INSERT 字段索引位置（从0开始）
         */
        private Integer insertFieldIndex;

        /**
         * 参数属性名（MyBatis）
         */
        private String parameterProperty;

        /**
         * 参数类型（MyBatis）
         */
        private String parameterType;
    }

    /**
     * 加密字段信息
     */
    @Data
    public static class EncryptFieldInfo {
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
         * 加密策略类名
         */
        private String fieldEncryptor;
    }
}

