package io.github.genkidoudou.core.logging;

import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * SQL 日志记录器
 * <p>
 * 提供统一的 SQL 执行日志记录格式，包括：
 * <ul>
 *   <li>SQL 执行前日志（SQL 语句、参数信息）</li>
 *   <li>SQL 执行成功日志（执行时间、结果）</li>
 *   <li>SQL 执行失败日志（错误信息、执行时间）</li>
 * </ul>
 * </p>
 * <p>
 * 日志格式统一，便于日志分析和监控。
 * </p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Slf4j
public class SqlLogger {

    /**
     * 日志前缀：SQL 执行前
     */
    private static final String PREFIX_PREPARED = "[PREPARED {}]";

    /**
     * 日志前缀：SQL 执行成功
     */
    private static final String PREFIX_RESULT = "[PREPARED {} RESULT]";

    /**
     * 日志前缀：SQL 执行失败
     */
    private static final String PREFIX_ERROR = "[PREPARED {} ERROR]";

    /**
     * 记录 SQL 执行前的日志
     *
     * @param sqlType      SQL 类型（QUERY/UPDATE/EXECUTE）
     * @param sql          SQL 语句
     * @param datasourceId 数据源标识
     */
    public static void logBeforeExecution(String sqlType, String sql, String datasourceId) {
        if (log.isInfoEnabled()) {
            log.info("{} [sql={}, sqlLength={}, datasource-id={}]",
                    formatPrefix(PREFIX_PREPARED, sqlType),
                    sql != null ? sql : "null",
                    sql != null ? sql.length() : 0,
                    datasourceId != null ? datasourceId : "default");
        }
    }

    /**
     * 记录最终 SQL（当 SQL 与原始 SQL 不同时）
     *
     * @param finalSql 最终执行的 SQL
     */
    public static void logFinalSql(String finalSql) {
        if (log.isDebugEnabled() && finalSql != null) {
            log.debug("[FINAL SQL] {}", finalSql);
        }
    }

    /**
     * 记录 SQL 执行成功的日志
     *
     * @param sqlType       SQL 类型（QUERY/UPDATE/EXECUTE）
     * @param sql           SQL 语句
     * @param executionTime 执行时间（毫秒）
     * @param result        执行结果（受影响行数、查询结果等）
     * @param datasourceId  数据源标识
     */
    public static void logSuccess(String sqlType, String sql, long executionTime, Object result, String datasourceId) {
        if (log.isInfoEnabled()) {
            log.info("{} Executed in {}ms [sql={}, result={}, datasource-id={}]",
                    formatPrefix(PREFIX_RESULT, sqlType),
                    executionTime,
                    sql != null ? sql : "null",
                    result,
                    datasourceId != null ? datasourceId : "default");
        }
    }

    /**
     * 记录 SQL 执行失败的日志
     *
     * @param sqlType       SQL 类型（QUERY/UPDATE/EXECUTE）
     * @param sql           SQL 语句
     * @param executionTime 执行时间（毫秒）
     * @param tables        SQL 中涉及的表名集合
     * @param datasourceId  数据源标识
     * @param error         异常信息
     */
    public static void logError(String sqlType, String sql, long executionTime,
                                Set<String> tables, String datasourceId, Throwable error) {
        log.error("{} Failed after {}ms [sql={}, sqlLength={}, tables={}, datasource-id={}], error: {}",
                formatPrefix(PREFIX_ERROR, sqlType),
                executionTime,
                sql != null ? sql : "null",
                sql != null ? sql.length() : 0,
                tables != null ? tables : "[]",
                datasourceId != null ? datasourceId : "default",
                error.getMessage(), error);
    }

    /**
     * 记录加密操作的日志
     *
     * @param tableName     表名
     * @param fieldName     字段名
     * @param parameterIndex 参数索引
     * @param datasourceId  数据源标识
     * @param originalValue 原始值（已脱敏）
     * @param encryptedValue 加密后的值（已脱敏）
     */
    public static void logEncryption(String tableName, String fieldName, int parameterIndex,
                                     String datasourceId, String originalValue, String encryptedValue) {
        if (log.isInfoEnabled()) {
            log.info("[ENCRYPTION] table={}, field={}, parameterIndex={}, datasource-id={}, originalLength={}, encryptedLength={}",
                    tableName,
                    fieldName,
                    parameterIndex,
                    datasourceId != null ? datasourceId : "default",
                    originalValue != null ? originalValue.length() : 0,
                    encryptedValue != null ? encryptedValue.length() : 0);
        }
    }

    /**
     * 记录解密操作的日志
     *
     * @param tableName     表名
     * @param fieldName     字段名
     * @param columnLabel   列标签
     * @param datasourceId  数据源标识
     * @param encryptedValue 加密后的值（已脱敏）
     * @param decryptedValue 解密后的值（已脱敏）
     */
    public static void logDecryption(String tableName, String fieldName, String columnLabel,
                                    String datasourceId, String encryptedValue, String decryptedValue) {
        if (log.isDebugEnabled()) {
            log.debug("[DECRYPTION] table={}, field={}, column={}, datasource-id={}, encryptedLength={}, decryptedLength={}",
                    tableName,
                    fieldName,
                    columnLabel,
                    datasourceId != null ? datasourceId : "default",
                    encryptedValue != null ? encryptedValue.length() : 0,
                    decryptedValue != null ? decryptedValue.length() : 0);
        }
    }

    /**
     * 格式化日志前缀
     *
     * @param prefix  前缀模板
     * @param sqlType SQL 类型
     * @return 格式化后的前缀
     */
    private static String formatPrefix(String prefix, String sqlType) {
        return prefix.replace("{}", sqlType);
    }
}

