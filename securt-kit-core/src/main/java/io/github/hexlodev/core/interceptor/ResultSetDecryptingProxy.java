package io.github.hexlodev.core.interceptor;

import cn.hutool.core.lang.Pair;
import cn.hutool.extra.spring.SpringUtil;
import io.github.hexlodev.core.TableCache;
import io.github.hexlodev.core.parser.SecurtkitUtils;
import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;

import java.sql.Statement;
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

    private ResultSetDecryptingProxy(ResultSet delegate, Set<String> tables, Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair, String sql) {
        this.delegate = delegate;
        this.tables = tables == null ? new HashSet<>() : new HashSet<>(tables);
        this.pair = pair;
        this.sql = sql;
    }

    static ResultSet wrap(ResultSet rs, Set<String> tables, Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair, String sql) {
        if (rs == null) {
            return null;
        }
        return (ResultSet) Proxy.newProxyInstance(
                rs.getClass().getClassLoader(),
                new Class[]{ResultSet.class},
                new ResultSetDecryptingProxy(rs, tables, pair, sql)
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
        try {
            if ("getString".equals(name)) {
                String value = (String) result;
                String column = resolveColumn(args);
                return maybeDecryptWithInfo(column, value);
            }
            if ("getObject".equals(name)) {
                String column = resolveColumn(args);
                if (result instanceof String) {
                    String value = (String) result;
                    return maybeDecryptWithInfo(column, value);
                }
                return result;
            }
        } catch (Throwable ignore) {
            // 解密失败不影响读取
        }

        return result;
    }


    private String resolveColumn(Object[] args) throws SQLException {
        if (args == null || args.length == 0) {
            return null;
        }
        if (args[0] instanceof Integer) {
            int idx = (Integer) args[0];
            ResultSetMetaData meta = delegate.getMetaData();
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
     * 解密值并返回解密结果和表名信息
     * @return String[0] 解密后的值, String[1] 表名（如果找到）
     */
    private String maybeDecryptWithInfo(String columnLabel, String value) throws SQLException {
        if (value == null || columnLabel == null) {
            return value;
        }
        String normalizedColumn = columnLabel.toLowerCase(Locale.ROOT);

        List<FieldEncryptorInfoDto> fieldEncryptorInfoDtos = this.pair.getValue();
        FieldEncryptorInfoDto fieldEncryptorInfoDto = fieldEncryptorInfoDtos.stream().filter(a -> a.getColumnName().toLowerCase(Locale.ROOT).equals(normalizedColumn)).findFirst().orElse(null);
        if (null != fieldEncryptorInfoDto) {
            Class<? extends FieldEncryptorStrategy> strategyClass = TableCache.getTableFieldEncryptInfo(fieldEncryptorInfoDto.getSourceTableName(), fieldEncryptorInfoDto.getSourceColumn());
            FieldEncryptorStrategy strategy = SpringUtil.getBean(strategyClass);
            return strategy.decryption(value);
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
            ResultSetMetaData meta = delegate.getMetaData();
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
        } catch (Throwable ignore) {
        }

        // fallback: 若只有一个表，则默认该表
        if ((tableName == null || tableName.isEmpty()) && tables != null && tables.size() == 1) {
            tableName = tables.iterator().next();
        }

        if (tableName == null || tableName.isEmpty()) {
            return value;
        }

        Class<? extends FieldEncryptorStrategy> strategyClass = TableCache.getTableFieldEncryptInfo(tableName, normalizedColumn);
        if (strategyClass == null) {
            return value;
        }
        FieldEncryptorStrategy strategy = SpringUtil.getBean(strategyClass);
        try {
            return strategy.decryption(value);
        } catch (Throwable t) {
            return value;
        }
    }
}


