package io.github.genkidoudou.core.config;

import lombok.extern.slf4j.Slf4j;

/**
 * 加解密通道模式持有者
 * <p>
 * 由 {@link ConfigInitializer} 在初始化阶段写入，供各通道（JDBC 拦截、MyBatis 插件）
 * 判断自身是否应生效，避免同一数据源上双通道同时加密。
 * </p>
 *
 * @author hexlodev
 * @since 1.3.0
 */
@Slf4j
public final class EncryptModeHolder {

    /**
     * 当前模式，默认 JDBC（兼容现状）
     */
    private static volatile FieldEncryptorProperties.Mode MODE = FieldEncryptorProperties.Mode.JDBC;

    private EncryptModeHolder() {
    }

    /**
     * 设置当前模式
     *
     * @param mode 模式，为 null 时按 JDBC 处理
     */
    public static void setMode(FieldEncryptorProperties.Mode mode) {
        MODE = mode == null ? FieldEncryptorProperties.Mode.JDBC : mode;
        log.debug("【securt-kit】Encrypt mode set to: {}", MODE);
    }

    /**
     * 获取当前模式
     *
     * @return 当前模式，不会为 null
     */
    public static FieldEncryptorProperties.Mode getMode() {
        return MODE;
    }

    /**
     * 是否为 JDBC 通道模式
     *
     * @return true 表示 JDBC 拦截通道生效
     */
    public static boolean isJdbc() {
        return MODE == FieldEncryptorProperties.Mode.JDBC;
    }

    /**
     * 是否为 MyBatis 通道模式
     *
     * @return true 表示 MyBatis 插件通道生效
     */
    public static boolean isMybatis() {
        return MODE == FieldEncryptorProperties.Mode.MYBATIS;
    }

    /**
     * 是否关闭所有通道
     *
     * @return true 表示不挂载任何加解密通道
     */
    public static boolean isOff() {
        return MODE == FieldEncryptorProperties.Mode.OFF;
    }

    /**
     * 重置为默认模式（用于测试或配置热更新）
     */
    public static void reset() {
        MODE = FieldEncryptorProperties.Mode.JDBC;
    }
}
