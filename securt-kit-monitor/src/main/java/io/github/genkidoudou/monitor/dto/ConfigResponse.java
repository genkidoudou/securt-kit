package io.github.genkidoudou.monitor.dto;

import lombok.Data;

import java.util.List;

/**
 * 配置信息响应（含 digest / mode 全览）
 */
@Data
public class ConfigResponse {
    private Boolean enable;
    private String mode;
    private String failurePolicy;
    private Boolean ignoreTableCase;
    private SqlParseCacheConfigInfo sqlParseCache;
    private SkipCommentConfigInfo skipComment;
    private DigestGlobalConfigInfo digest;
    private List<TableConfigInfo> tables;

    @Data
    public static class SqlParseCacheConfigInfo {
        private Boolean enable;
        private Integer maxSize;
    }

    @Data
    public static class SkipCommentConfigInfo {
        private Boolean enable;
        private String token;
    }

    @Data
    public static class DigestGlobalConfigInfo {
        private String digestStrategy;
        private String digestPartialUpdate;
        private Boolean digestVerifyOnRead;
        private String digestFailurePolicy;
        /** 是否已配置 HMAC key（不回显密钥） */
        private Boolean digestHmacKeyConfigured;
    }

    @Data
    public static class TableConfigInfo {
        private String tableName;
        private String datasourceId;
        private List<FieldConfigInfo> fields;
        private List<DigestRuleInfo> digest;
        private PrimaryKeyInfo primaryKey;
    }

    @Data
    public static class FieldConfigInfo {
        private String fieldName;
        private String strategy;
    }

    @Data
    public static class DigestRuleInfo {
        private List<String> sourceFields;
        private String targetField;
        private String strategy;
        private String partialUpdate;
        private Boolean verifyOnRead;
        private String failurePolicy;
    }
}

