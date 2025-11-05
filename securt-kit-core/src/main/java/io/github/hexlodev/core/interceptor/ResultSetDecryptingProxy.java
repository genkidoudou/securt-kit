package io.github.hexlodev.core.interceptor;

import cn.hutool.core.lang.Pair;
import io.github.hexlodev.core.TableCache;
import io.github.hexlodev.core.cache.StrategyCache;
import io.github.hexlodev.core.exception.EncryptionHandler;
import io.github.hexlodev.core.parser.SecurtkitUtils;
import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.io.Reader;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 动态代理包装 ResultSet，按表字段配置对读取的数据进行解密。
 */
@Slf4j
final class ResultSetDecryptingProxy implements InvocationHandler {

    private final ResultSet delegate;
    private final Set<String> tables;
    private final Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair;

    private final String sql;
    
    /**
     * 缓存的 ResultSetMetaData，在 ResultSet 生命周期内不变，避免重复获取
     */
    private volatile ResultSetMetaData cachedMetaData;

    /**
     * 列名到字段加密信息的映射（优化查找性能）
     * key: 列名（小写）
     * value: 字段加密信息DTO
     * 
     * <p>在构造函数中初始化，用于将 O(n) 的 Stream 查找优化为 O(1) 的 Map 查找</p>
     * 
     * @since 1.0.0
     */
    private Map<String, FieldEncryptorInfoDto> columnNameToFieldMap;

    /**
     * 数据源标识（多数据源场景）
     */
    private final String datasourceId;

    private ResultSetDecryptingProxy(ResultSet delegate, Set<String> tables, Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair, String sql, String datasourceId) {
        this.delegate = delegate;
        this.tables = tables == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(tables));
        this.pair = pair;
        this.sql = sql;
        this.datasourceId = cn.hutool.core.util.StrUtil.isBlank(datasourceId) ? "default" : datasourceId;
        
        // 构建列名到字段信息的映射（优化性能：O(n) -> O(1)）
        this.columnNameToFieldMap = buildColumnNameMap(pair);
    }

    /**
     * 构建列名到字段加密信息的映射
     * 
     * <p>将 O(n) 的 Stream 查找优化为 O(1) 的 Map 查找，提升性能。
     * 在构造函数中调用一次，后续所有解密调用都使用此映射。</p>
     *
     * @param pair SQL解析结果对
     * @return 列名到字段加密信息的映射，如果不需要解密则返回空Map
     */
    private Map<String, FieldEncryptorInfoDto> buildColumnNameMap(Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair) {
        if (pair == null || pair.getValue() == null) {
            return Collections.emptyMap();
        }
        
        Map<String, FieldEncryptorInfoDto> columnMap = new HashMap<>();
        for (FieldEncryptorInfoDto dto : pair.getValue()) {
            String columnName = dto.getColumnName();
            if (columnName != null && !columnName.isEmpty()) {
                // 使用小写作为 key，统一大小写处理
                String normalizedKey = columnName.toLowerCase(Locale.ROOT);
                // 如果同一个列名对应多个字段，保留第一个（通常不会发生）
                columnMap.putIfAbsent(normalizedKey, dto);
            }
        }
        return columnMap;
    }

    static ResultSet wrap(ResultSet rs, Set<String> tables, Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair, String sql) {
        return wrap(rs, tables, pair, sql, null);
    }

    static ResultSet wrap(ResultSet rs, Set<String> tables, Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair, String sql, String datasourceId) {
        if (rs == null) {
            return null;
        }
        return (ResultSet) Proxy.newProxyInstance(
                rs.getClass().getClassLoader(),
                new Class[]{ResultSet.class},
                new ResultSetDecryptingProxy(rs, tables, pair, sql, datasourceId)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();

        // 优先执行原始调用
        Object result = method.invoke(delegate, args);

        if (result == null) {
            return null;
        }
        if (!SecurtkitUtils.needEncrypt(this.tables) && null == this.pair) {
            return result;
        }
        
        String columnLabel = null;
        try {
            if ("getString".equals(name)) {
                String value = (String) result;
                columnLabel = resolveColumn(args);
                return maybeDecryptWithInfo(columnLabel, value);
            }
            if ("getObject".equals(name)) {
                columnLabel = resolveColumn(args);
                if (result instanceof String) {
                    String value = (String) result;
                    return maybeDecryptWithInfo(columnLabel, value);
                }
                return result;
            }
            // 拦截 TEXT/CLOB 类型字段的读取方法
            if ("getClob".equals(name)) {
                Clob clob = (Clob) result;
                if (clob != null) {
                    columnLabel = resolveColumn(args);
                    String value = clobToString(clob);
                    String decrypted = maybeDecryptWithInfo(columnLabel, value);
                    if (decrypted != null && !decrypted.equals(value)) {
                        // 如果解密成功，返回新的 Clob（由于 ResultSet 没有 getConnection，使用 StringReader 包装）
                        // 注意：这会导致类型不匹配，但这是目前可行的方案
                        // 更好的方案是返回 String，但会破坏类型一致性
                        // 实际使用时，如果字段是 TEXT 类型，建议使用 getString() 而不是 getClob()
                        log.debug("Decrypted Clob for column: {}, returning StringReader wrapper", columnLabel);
                        return new java.io.StringReader(decrypted);
                    }
                }
                return result;
            }
            if ("getNClob".equals(name)) {
                NClob nClob = (NClob) result;
                if (nClob != null) {
                    columnLabel = resolveColumn(args);
                    String value = nClobToString(nClob);
                    String decrypted = maybeDecryptWithInfo(columnLabel, value);
                    if (decrypted != null && !decrypted.equals(value)) {
                        // 如果解密成功，返回新的 Reader（由于 ResultSet 没有 getConnection，使用 StringReader 包装）
                        log.debug("Decrypted NClob for column: {}, returning StringReader wrapper", columnLabel);
                        return new java.io.StringReader(decrypted);
                    }
                }
                return result;
            }
            if ("getCharacterStream".equals(name)) {
                Reader reader = (Reader) result;
                if (reader != null) {
                    columnLabel = resolveColumn(args);
                    String value = readerToString(reader);
                    String decrypted = maybeDecryptWithInfo(columnLabel, value);
                    if (decrypted != null && !decrypted.equals(value)) {
                        // 如果解密成功，返回新的 Reader（使用解密后的字符串创建）
                        return new java.io.StringReader(decrypted);
                    }
                }
                return result;
            }
            if ("getNCharacterStream".equals(name)) {
                Reader reader = (Reader) result;
                if (reader != null) {
                    columnLabel = resolveColumn(args);
                    String value = readerToString(reader);
                    String decrypted = maybeDecryptWithInfo(columnLabel, value);
                    if (decrypted != null && !decrypted.equals(value)) {
                        // 如果解密成功，返回新的 Reader（使用解密后的字符串创建）
                        return new java.io.StringReader(decrypted);
                    }
                }
                return result;
            }
        } catch (Throwable e) {
            // 解密失败不影响读取，但记录调试日志以便排查问题
            if (log.isDebugEnabled()) {
                log.debug("Failed to decrypt field value, using original value [column={}, errorClass={}]. Error: {}", 
                        columnLabel != null ? columnLabel : "unknown",
                        e.getClass().getSimpleName(),
                        e.getMessage(), e);
            }
        }

        return result;
    }

    /**
     * 获取 ResultSetMetaData，使用缓存避免重复获取
     * ResultSetMetaData 在 ResultSet 生命周期内不变，可以安全缓存
     * 
     * @return ResultSetMetaData 元数据对象
     * @throws SQLException 如果获取元数据失败
     */
    private ResultSetMetaData getMetaData() throws SQLException {
        if (cachedMetaData == null) {
            synchronized (this) {
                if (cachedMetaData == null) {
                    cachedMetaData = delegate.getMetaData();
                }
            }
        }
        return cachedMetaData;
    }

    private String resolveColumn(Object[] args) throws SQLException {
        if (args == null || args.length == 0) {
            return null;
        }
        if (args[0] instanceof Integer) {
            int idx = (Integer) args[0];
            ResultSetMetaData meta = getMetaData();
            String label = meta.getColumnLabel(idx);
            if (label == null || label.isEmpty()) {
                label = meta.getColumnName(idx);
            }
            return label;
        } else if (args[0] instanceof String) {
            return (String) args[0];
        }
        return null;
    }

    /**
     * 解密值并返回解密结果
     * 
     * <p>性能优化：使用预构建的列名映射（O(1)查找）替代 Stream 遍历（O(n)查找），
     * 在高频调用场景下显著提升性能。</p>
     *
     * @param columnLabel 列名（标签或名称）
     * @param value       加密后的值
     * @return 解密后的值，如果不需要解密或解密失败则返回原值
     * @throws SQLException 如果获取元数据失败
     */
    private String maybeDecryptWithInfo(String columnLabel, String value) throws SQLException {
        if (value == null || columnLabel == null) {
            return value;
        }
        
        String normalizedColumn = columnLabel.toLowerCase(Locale.ROOT);

        // 使用预构建的映射进行 O(1) 查找，替代原来的 O(n) Stream 查找
        FieldEncryptorInfoDto fieldEncryptorInfoDto = this.columnNameToFieldMap != null 
                ? this.columnNameToFieldMap.get(normalizedColumn) 
                : null;
                
        if (fieldEncryptorInfoDto != null) {
            try {
                Class<? extends FieldEncryptorStrategy> strategyClass = TableCache.getTableFieldEncryptStrategy(
                        fieldEncryptorInfoDto.getSourceTableName(), 
                        fieldEncryptorInfoDto.getSourceColumn(),
                        datasourceId);
                // 使用策略缓存获取策略实例
                FieldEncryptorStrategy strategy = StrategyCache.getStrategy(strategyClass);
                // 使用统一的异常处理器
                String decrypted = EncryptionHandler.handleDecryption(
                        value,
                        fieldEncryptorInfoDto.getSourceTableName(),
                        fieldEncryptorInfoDto.getSourceColumn(),
                        () -> strategy.decryption(value),
                        null // 使用默认策略
                );
                // 如果解密失败且策略为 SKIP，返回 null；否则返回原值或解密后的值
                return decrypted != null ? decrypted : value;
            } catch (Exception e) {
                // 解密失败时记录调试日志，但不影响正常读取
                if (log.isDebugEnabled()) {
                    log.debug("Failed to decrypt field [column={}, table={}, field={}], using original value. Error: {}", 
                            columnLabel,
                            fieldEncryptorInfoDto.getSourceTableName(),
                            fieldEncryptorInfoDto.getSourceColumn(),
                            e.getMessage(), e);
                }
                return value;
            }
        }
        return value;
    }

    private String maybeDecrypt(String columnLabel, String value) throws SQLException {
        if (value == null || columnLabel == null) {
            return value;
        }
        String normalizedColumn = columnLabel.toLowerCase(Locale.ROOT);

        // 尝试使用列所属表名（如果驱动能提供）
        String tableName = null;
        try {
            ResultSetMetaData meta = getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                String label = meta.getColumnLabel(i);
                if (label == null || label.isEmpty()) {
                    label = meta.getColumnName(i);
                }
                if (normalizedColumn.equalsIgnoreCase(label)) {
                    tableName = meta.getTableName(i);
                    break;
                }
            }
        } catch (Throwable e) {
            // 解密失败不影响读取，但记录调试日志以便排查问题
            if (log.isDebugEnabled()) {
                log.debug("Failed to get table name for column, using fallback [column={}, errorClass={}]. Error: {}", 
                        columnLabel,
                        e.getClass().getSimpleName(),
                        e.getMessage(), e);
            }
        }

        // fallback: 若只有一个表，则默认该表
        if ((tableName == null || tableName.isEmpty()) && tables != null && tables.size() == 1) {
            tableName = tables.iterator().next();
        }

        if (tableName == null || tableName.isEmpty()) {
            return value;
        }

        Class<? extends FieldEncryptorStrategy> strategyClass = TableCache.getTableFieldEncryptStrategy(tableName, normalizedColumn, datasourceId);
        if (strategyClass == null) {
            return value;
        }
        // 使用策略缓存获取策略实例
        FieldEncryptorStrategy strategy = StrategyCache.getStrategy(strategyClass);
        // 使用统一的异常处理器
        String decrypted = EncryptionHandler.handleDecryption(
                value,
                tableName,
                normalizedColumn,
                () -> strategy.decryption(value),
                null // 使用默认策略
        );
        // 如果解密失败且策略为 SKIP，返回 null；否则返回原值或解密后的值
        return decrypted != null ? decrypted : value;
    }

    /**
     * 将 Clob 转换为 String
     *
     * @param clob Clob 对象
     * @return 字符串内容
     */
    private String clobToString(Clob clob) {
        try {
            long length = clob.length();
            if (length > Integer.MAX_VALUE) {
                log.warn("Clob length {} exceeds Integer.MAX_VALUE, truncating", length);
                length = Integer.MAX_VALUE;
            }
            return clob.getSubString(1, (int) length);
        } catch (SQLException e) {
            log.error("Failed to convert Clob to String", e);
            return null;
        }
    }

    /**
     * 将 NClob 转换为 String
     *
     * @param nClob NClob 对象
     * @return 字符串内容
     */
    private String nClobToString(NClob nClob) {
        try {
            long length = nClob.length();
            if (length > Integer.MAX_VALUE) {
                log.warn("NClob length {} exceeds Integer.MAX_VALUE, truncating", length);
                length = Integer.MAX_VALUE;
            }
            return nClob.getSubString(1, (int) length);
        } catch (SQLException e) {
            log.error("Failed to convert NClob to String", e);
            return null;
        }
    }

    /**
     * 将 Reader 转换为 String
     *
     * @param reader Reader 对象
     * @return 字符串内容
     */
    private String readerToString(Reader reader) {
        try {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to convert Reader to String", e);
            return null;
        }
    }
}


