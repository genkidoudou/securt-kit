package io.github.genkidoudou.core.crypto;

import lombok.extern.slf4j.Slf4j;

/**
 * 字段加解密门面持有者
 * <p>
 * 各通道通过 {@link #get()} 获取唯一门面实例；Starter 可在自动配置阶段
 * 通过 {@link #set(FieldCryptoService)} 替换为自定义实现（如注入 Spring Bean）。
 * </p>
 *
 * @author hexlodev
 * @since 1.3.0
 */
@Slf4j
public final class FieldCryptoServiceHolder {

    /**
     * 当前门面实例，默认使用 {@link DefaultFieldCryptoService}
     */
    private static volatile FieldCryptoService INSTANCE = new DefaultFieldCryptoService();

    private FieldCryptoServiceHolder() {
    }

    /**
     * 设置门面实例
     *
     * @param service 门面实现，为 null 时忽略
     */
    public static void set(FieldCryptoService service) {
        if (service == null) {
            log.warn("【securt-kit】FieldCryptoService is null, keeping current instance: {}",
                    INSTANCE.getClass().getName());
            return;
        }
        INSTANCE = service;
        log.debug("【securt-kit】FieldCryptoService set to: {}", service.getClass().getName());
    }

    /**
     * 获取门面实例
     *
     * @return 门面实例，不会为 null
     */
    public static FieldCryptoService get() {
        return INSTANCE;
    }

    /**
     * 重置为默认实现（用于测试或配置热更新）
     */
    public static void reset() {
        INSTANCE = new DefaultFieldCryptoService();
    }
}
