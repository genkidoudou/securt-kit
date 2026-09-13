package io.github.genkidoudou.monitor.ops;

import io.github.genkidoudou.monitor.MonitorProperties;
import io.github.genkidoudou.monitor.dto.PrimaryKeyInfo;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

/**
 * 主键列解析：配置 → MyBatis-Plus TableInfo → none。
 */
public final class PrimaryKeyResolver {

    private PrimaryKeyResolver() {
    }

    public static PrimaryKeyInfo resolve(MonitorProperties properties, String table, String manualIdColumn) {
        if (manualIdColumn != null && !manualIdColumn.trim().isEmpty()) {
            PrimaryKeyInfo info = new PrimaryKeyInfo();
            info.setColumn(manualIdColumn.trim());
            info.setSource("manual");
            return info;
        }
        if (table == null || table.trim().isEmpty()) {
            return none();
        }
        String canonical = canonicalize(table);
        if (properties != null && properties.getTablePrimaryKeys() != null) {
            Map<String, String> map = properties.getTablePrimaryKeys();
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (e.getKey() != null && canonicalize(e.getKey()).equals(canonical)
                        && e.getValue() != null && !e.getValue().trim().isEmpty()) {
                    PrimaryKeyInfo info = new PrimaryKeyInfo();
                    info.setColumn(e.getValue().trim());
                    info.setSource("config");
                    return info;
                }
            }
        }
        String fromTableInfo = resolveFromMybatisPlus(canonical);
        if (fromTableInfo != null) {
            PrimaryKeyInfo info = new PrimaryKeyInfo();
            info.setColumn(fromTableInfo);
            info.setSource("tableInfo");
            return info;
        }
        return none();
    }

    /**
     * 解析顺序不含 manual（用于页面默认展示）。
     */
    public static PrimaryKeyInfo resolveDefault(MonitorProperties properties, String table) {
        return resolve(properties, table, null);
    }

    private static PrimaryKeyInfo none() {
        PrimaryKeyInfo info = new PrimaryKeyInfo();
        info.setColumn(null);
        info.setSource("none");
        return info;
    }

    static String canonicalize(String table) {
        String t = table.trim();
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("`") && t.endsWith("`"))) {
            t = t.substring(1, t.length() - 1);
        }
        return t.toLowerCase(Locale.ROOT);
    }

    private static String resolveFromMybatisPlus(String table) {
        try {
            Class<?> helper = Class.forName("com.baomidou.mybatisplus.core.metadata.TableInfoHelper");
            Method getTableInfos = null;
            for (Method m : helper.getMethods()) {
                if ("getTableInfos".equals(m.getName()) && m.getParameterCount() == 0) {
                    getTableInfos = m;
                    break;
                }
            }
            if (getTableInfos == null) {
                return null;
            }
            Object list = getTableInfos.invoke(null);
            if (!(list instanceof Iterable)) {
                return null;
            }
            for (Object tableInfo : (Iterable<?>) list) {
                Method getTableName = tableInfo.getClass().getMethod("getTableName");
                Object name = getTableName.invoke(tableInfo);
                if (name == null || !canonicalize(String.valueOf(name)).equals(table)) {
                    continue;
                }
                Method getKeyColumn = tableInfo.getClass().getMethod("getKeyColumn");
                Object key = getKeyColumn.invoke(tableInfo);
                if (key != null && !String.valueOf(key).trim().isEmpty()) {
                    return String.valueOf(key).trim();
                }
            }
        } catch (ClassNotFoundException ignore) {
            return null;
        } catch (Exception ignore) {
            return null;
        }
        return null;
    }
}
