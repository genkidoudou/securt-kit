package io.github.genkidoudou.monitor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 监控页面配置属性
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "securtkit.monitor")
public class MonitorProperties {
    private boolean enabled = true;
    private String username = "admin";
    private String password = "admin123";
    private String path = "/monitor";

    /**
     * 表白名单主键列：tableName → idColumn（优先于 TableInfo）
     */
    private Map<String, String> tablePrimaryKeys = new LinkedHashMap<>();

    /**
     * 是否允许刷数空 WHERE（默认否）
     */
    private boolean batchAllowEmptyWhere = false;

    /**
     * 是否允许对未配置加密/摘要的表刷数（默认否）
     */
    private boolean batchAllowUnconfiguredTables = false;

    /**
     * 异步刷数作业配置。
     */
    private BatchSettings batch = new BatchSettings();

    @Data
    public static class BatchSettings {
        /** memory | database */
        private String store = "memory";
        private int maxRows = 20000;
        private int chunkSize = 200;
        private int sampleSize = 20;
        private int maxFailureRecords = 1000;
        private long memoryTtlMs = 3600000L;
    }
}
