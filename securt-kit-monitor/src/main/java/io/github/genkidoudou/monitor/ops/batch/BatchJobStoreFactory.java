package io.github.genkidoudou.monitor.ops.batch;

import io.github.genkidoudou.monitor.MonitorProperties;

import javax.sql.DataSource;

/**
 * 按配置创建作业仓储。
 */
public final class BatchJobStoreFactory {

    private BatchJobStoreFactory() {
    }

    public static BatchJobStore create(MonitorProperties properties, DataSource dataSource) {
        MonitorProperties props = properties != null ? properties : new MonitorProperties();
        MonitorProperties.BatchSettings batch = props.getBatch() != null ? props.getBatch() : new MonitorProperties.BatchSettings();
        String mode = batch.getStore() == null ? "memory" : batch.getStore().trim().toLowerCase();
        if ("database".equals(mode) || "db".equals(mode) || "jdbc".equals(mode)) {
            if (dataSource == null) {
                throw new IllegalStateException("batch.store=database 需要可用 DataSource，请改用 memory 或配置数据源");
            }
            JdbcBatchJobStore jdbc = new JdbcBatchJobStore(dataSource);
            try {
                jdbc.ensureSchema();
            } catch (Exception e) {
                throw new IllegalStateException("database 作业表初始化失败: " + e.getMessage() + "；可改 securtkit.monitor.batch.store=memory", e);
            }
            jdbc.normalizeLeakedRunningToPaused();
            return jdbc;
        }
        if (!"memory".equals(mode) && !mode.isEmpty()) {
            throw new IllegalArgumentException("未知 batch.store=" + batch.getStore() + "，仅支持 memory|database");
        }
        return new MemoryBatchJobStore(batch.getMemoryTtlMs());
    }
}
