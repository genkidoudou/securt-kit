package io.github.hexlodev.core.interceptor;

import cn.hutool.core.lang.Pair;
import io.github.hexlodev.core.logging.SqlLogger;
import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
import lombok.extern.slf4j.Slf4j;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * SQL 执行器
 * <p>
 * 负责 SQL 执行、日志记录、性能监控和结果集包装
 * </p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Slf4j
public class SqlExecutor {

    /**
     * 真实的 PreparedStatement 对象
     */
    private final PreparedStatement delegate;

    /**
     * 原始 SQL 语句
     */
    private final String sql;

    /**
     * 数据源标识
     */
    private final String datasourceId;

    /**
     * SQL 中涉及的表名集合
     */
    private final Set<String> tables;

    /**
     * SQL 解析结果
     */
    private final Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> sqlParseResult;

    /**
     * 构造函数
     * <p>
     * 创建 SQL 执行器实例，用于统一处理 SQL 执行、日志记录和性能监控。
     * </p>
     *
     * @param delegate      真实的 PreparedStatement 对象，不能为 null
     * @param sql           原始 SQL 语句，不能为 null
     * @param datasourceId  数据源标识，如果为 null 则使用 "default"
     * @param tables        SQL 中涉及的表名集合，可以为 null
     * @param sqlParseResult SQL 解析结果，用于结果集包装，可以为 null
     */
    public SqlExecutor(PreparedStatement delegate, String sql, String datasourceId,
                       Set<String> tables,
                       Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> sqlParseResult) {
        this.delegate = delegate;
        this.sql = sql;
        this.datasourceId = datasourceId != null ? datasourceId : "default";
        this.tables = tables != null ? new HashSet<>(tables) : Collections.emptySet();
        this.sqlParseResult = sqlParseResult;
    }

    /**
     * 执行查询
     * <p>
     * 执行 SQL 查询并记录日志，对结果集进行包装以实现自动解密。
     * </p>
     *
     * @param parameterValues 参数值映射（用于日志），可以为 null
     * @return 查询结果集，已包装为支持自动解密的 ResultSet
     * @throws SQLException 如果 SQL 执行失败
     */
    public ResultSet executeQuery(Map<Integer, Object> parameterValues) throws SQLException {
        String finalSql = buildFinalSql(parameterValues);
        long startTime = System.currentTimeMillis();

        // 记录执行前日志
        SqlLogger.logBeforeExecution("QUERY", sql, datasourceId);
        
        // 如果最终 SQL 与原始 SQL 不同，记录最终 SQL
        if (!sql.equals(finalSql)) {
            SqlLogger.logFinalSql(finalSql);
        }

        try {
            ResultSet resultSet = delegate.executeQuery();
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;

            // 记录成功日志
            SqlLogger.logSuccess("QUERY", finalSql, executionTime, "SUCCESS", datasourceId);

            // 包装结果集以实现自动解密
            return ResultSetDecryptingProxy.wrap(resultSet, this.tables, this.sqlParseResult, this.sql, this.datasourceId);
        } catch (SQLException e) {
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
            
            // 记录错误日志
            SqlLogger.logError("QUERY", finalSql, executionTime, tables, datasourceId, e);
            throw e;
        }
    }

    /**
     * 执行更新
     * <p>
     * 执行 SQL 更新（INSERT/UPDATE/DELETE）并记录日志和性能指标。
     * </p>
     *
     * @param parameterValues 参数值映射（用于日志），可以为 null
     * @return 受影响的行数
     * @throws SQLException 如果 SQL 执行失败
     */
    public int executeUpdate(Map<Integer, Object> parameterValues) throws SQLException {
        String finalSql = buildFinalSql(parameterValues);
        long startTime = System.currentTimeMillis();

        // 记录执行前日志
        SqlLogger.logBeforeExecution("UPDATE", sql, datasourceId);
        
        // 如果最终 SQL 与原始 SQL 不同，记录最终 SQL
        if (!sql.equals(finalSql)) {
            SqlLogger.logFinalSql(finalSql);
        }

        try {
            int result = delegate.executeUpdate();
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;

            // 记录成功日志
            SqlLogger.logSuccess("UPDATE", finalSql, executionTime, result, datasourceId);
            return result;
        } catch (SQLException e) {
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
            
            // 记录错误日志
            SqlLogger.logError("UPDATE", finalSql, executionTime, tables, datasourceId, e);
            throw e;
        }
    }

    /**
     * 执行 SQL（通用）
     * <p>
     * 通用执行方法，可用于执行任何类型的 SQL 语句。记录 SQL 语句和执行时间。
     * </p>
     *
     * @param parameterValues 参数值映射（用于日志），可以为 null
     * @return 如果第一个结果是 ResultSet 对象则返回 true，否则返回 false
     * @throws SQLException 如果 SQL 执行失败
     */
    public boolean execute(Map<Integer, Object> parameterValues) throws SQLException {
        String finalSql = buildFinalSql(parameterValues);
        long startTime = System.currentTimeMillis();

        // 记录执行前日志
        SqlLogger.logBeforeExecution("EXECUTE", sql, datasourceId);
        
        // 如果最终 SQL 与原始 SQL 不同，记录最终 SQL
        if (!sql.equals(finalSql)) {
            SqlLogger.logFinalSql(finalSql);
        }

        try {
            boolean result = delegate.execute();
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;

            // 记录成功日志
            SqlLogger.logSuccess("EXECUTE", finalSql, executionTime, result, datasourceId);
            return result;
        } catch (SQLException e) {
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
            
            // 记录错误日志
            SqlLogger.logError("EXECUTE", finalSql, executionTime, tables, datasourceId, e);
            throw e;
        }
    }

    /**
     * 生成最终 SQL（用于日志）
     * <p>
     * 将 SQL 中的占位符（?）替换为实际参数值，用于日志输出。
     * 注意：此方法仅用于日志记录，不用于实际 SQL 执行。
     * </p>
     *
     * @param parameterValues 参数值映射，key 为参数索引（从1开始），value 为参数值
     * @return 将占位符替换为实际参数值的 SQL，如果参数值为空则返回原始 SQL
     */
    public String buildFinalSql(Map<Integer, Object> parameterValues) {
        if (parameterValues == null || parameterValues.isEmpty()) {
            return sql;
        }

        StringBuilder finalSql = new StringBuilder();
        int paramIndex = 0; // 占位符索引（从1开始，对应JDBC参数索引）

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '?') {
                paramIndex++;
                Object value = parameterValues.get(paramIndex);
                if (value != null) {
                    finalSql.append(formatSqlValue(value));
                } else {
                    // 如果没有设置该参数，保留?占位符
                    finalSql.append('?');
                }
            } else {
                finalSql.append(c);
            }
        }

        return finalSql.toString();
    }

    /**
     * 格式化 SQL 值（添加引号等）
     * <p>
     * 将参数值格式化为 SQL 字符串格式，用于日志输出。
     * </p>
     *
     * @param value 参数值，可以为 null
     * @return 格式化后的 SQL 值字符串（如：'value'、123、NULL）
     */
    private String formatSqlValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof String) {
            // 转义单引号，使用 StringBuilder 优化字符串拼接
            String str = ((String) value).replace("'", "''");
            StringBuilder sb = new StringBuilder(str.length() + 2);
            sb.append('\'').append(str).append('\'');
            return sb.toString();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        // 其他类型转为字符串并转义
        String str = String.valueOf(value);
        String escaped = str.replace("'", "''");
        StringBuilder sb = new StringBuilder(escaped.length() + 2);
        sb.append('\'').append(escaped).append('\'');
        return sb.toString();
    }

}

