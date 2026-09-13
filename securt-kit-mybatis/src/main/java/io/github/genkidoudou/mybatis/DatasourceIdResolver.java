package io.github.genkidoudou.mybatis;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * 数据源标识解析器
 * <p>
 * MyBatis 通道无法像 JDBC 通道那样从连接 URL 上拿到数据源标识，
 * 因此优先读取 dynamic-datasource 的线程上下文；不存在时回退为 {@code default}。
 * </p>
 * <p>
 * 通过反射访问 dynamic-datasource，避免本模块产生编译期依赖。
 * </p>
 *
 * @author hexlodev
 * @since 1.3.0
 */
@Slf4j
public final class DatasourceIdResolver {

    /**
     * 默认数据源标识，与 core 中 {@code TableCache} 的默认值保持一致
     */
    public static final String DEFAULT_DATASOURCE_ID = "default";

    /**
     * dynamic-datasource 上下文持有者类名
     */
    private static final String DYNAMIC_DATASOURCE_HOLDER =
            "com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder";

    /**
     * 缓存反射得到的 peek 方法；null 表示 classpath 中不存在 dynamic-datasource
     */
    private static volatile Method peekMethod;

    /**
     * 是否已尝试过反射查找，避免每次调用都触发 ClassNotFoundException
     */
    private static volatile boolean resolved;

    private DatasourceIdResolver() {
    }

    /**
     * 解析当前线程的数据源标识
     *
     * @return 数据源标识，未使用多数据源时返回 {@link #DEFAULT_DATASOURCE_ID}
     */
    public static String resolve() {
        Method method = lookupPeekMethod();
        if (method == null) {
            return DEFAULT_DATASOURCE_ID;
        }
        try {
            Object peek = method.invoke(null);
            if (peek != null && StrUtil.isNotBlank(peek.toString())) {
                return peek.toString();
            }
        } catch (Throwable ignored) {
            // 上下文不可用时按默认数据源处理，不影响 SQL 执行
        }
        return DEFAULT_DATASOURCE_ID;
    }

    private static Method lookupPeekMethod() {
        if (resolved) {
            return peekMethod;
        }
        synchronized (DatasourceIdResolver.class) {
            if (resolved) {
                return peekMethod;
            }
            try {
                Class<?> holder = Class.forName(DYNAMIC_DATASOURCE_HOLDER);
                peekMethod = holder.getMethod("peek");
                log.debug("【securt-kit】dynamic-datasource detected, datasource-id resolved from thread context");
            } catch (Throwable ignored) {
                peekMethod = null;
                log.debug("【securt-kit】dynamic-datasource not found, using datasource-id: {}", DEFAULT_DATASOURCE_ID);
            }
            resolved = true;
            return peekMethod;
        }
    }

    /**
     * 重置反射缓存（用于测试）
     */
    public static void reset() {
        synchronized (DatasourceIdResolver.class) {
            peekMethod = null;
            resolved = false;
        }
    }
}
