package io.github.hexlodev.ui.monitor.dto;

import lombok.Data;

import java.util.List;

/**
 * 配置信息响应
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
public class ConfigResponse {
    /**
     * 是否启用加密功能
     */
    private Boolean enable;

    /**
     * 失败处理策略
     */
    private String failurePolicy;

    /**
     * SQL解析缓存配置
     */
    private SqlParseCacheConfigInfo sqlParseCache;

    /**
     * 表配置列表
     */
    private List<TableConfigInfo> tables;

    /**
     * SQL解析缓存配置信息
     */
    @Data
    public static class SqlParseCacheConfigInfo {
        /**
         * 是否启用缓存
         */
        private Boolean enable;

        /**
         * 缓存最大容量
         */
        private Integer maxSize;
    }

    /**
     * 表配置信息
     */
    @Data
    public static class TableConfigInfo {
        /**
         * 表名
         */
        private String tableName;

        /**
         * 数据源标识（可选）
         */
        private String datasourceId;

        /**
         * 字段配置列表
         */
        private List<FieldConfigInfo> fields;
    }

    /**
     * 字段配置信息
     */
    @Data
    public static class FieldConfigInfo {
        /**
         * 字段名
         */
        private String fieldName;

        /**
         * 策略类名（全限定名）
         */
        private String strategy;
    }
}

