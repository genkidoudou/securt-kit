package io.github.hexlodev.core.cache;

import cn.hutool.extra.spring.SpringUtil;
import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 加密策略实例缓存
 * <p>
 * 缓存 FieldEncryptorStrategy 实例，避免频繁调用 SpringUtil.getBean()，
 * 显著提升加密/解密操作的性能。
 * </p>
 *
 * <p>特性：</p>
 * <ul>
 *   <li>线程安全：使用 ConcurrentHashMap 保证并发安全</li>
 *   <li>懒加载：首次使用时才从 Spring 容器获取或实例化</li>
 *   <li>降级策略：Spring 环境不可用时，尝试直接实例化</li>
 *   <li>支持手动注册：可用于测试或非 Spring 环境</li>
 * </ul>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Slf4j
public class StrategyCache {

    /**
     * 策略类 -> 策略实例缓存
     * Key: 策略类的 Class 对象
     * Value: 策略实例（单例）
     */
    private static final Map<Class<? extends FieldEncryptorStrategy>, FieldEncryptorStrategy> STRATEGY_CACHE =
            new ConcurrentHashMap<>();

    /**
     * 获取策略实例
     * <p>
     * 首先从缓存中查找，如果不存在则：
     * 1. 尝试从 Spring 容器获取 Bean
     * 2. 如果 Spring 环境不可用，尝试直接实例化
     * </p>
     *
     * @param strategyClass 策略类
     * @return 策略实例
     * @throws RuntimeException 如果无法获取或创建策略实例
     */
    public static FieldEncryptorStrategy getStrategy(Class<? extends FieldEncryptorStrategy> strategyClass) {
        if (strategyClass == null) {
            throw new IllegalArgumentException("Strategy class cannot be null");
        }

        return STRATEGY_CACHE.computeIfAbsent(strategyClass, clazz -> {
            try {
                // 优先从 Spring 容器获取
                FieldEncryptorStrategy strategy = SpringUtil.getBean(clazz);
                log.debug("Loaded strategy from Spring: {}", clazz.getName());
                return strategy;
            } catch (Exception e) {
                log.warn("Failed to get strategy bean from Spring: {}, attempting direct instantiation", clazz.getName(), e);
                // 降级：尝试直接实例化（用于非 Spring 环境或测试）
                try {
                    FieldEncryptorStrategy strategy = clazz.getDeclaredConstructor().newInstance();
                    log.debug("Created strategy instance directly: {}", clazz.getName());
                    return strategy;
                } catch (Exception ex) {
                    String errorMsg = String.format("Cannot create strategy instance for class: %s", clazz.getName());
                    log.error(errorMsg, ex);
                    throw new RuntimeException(errorMsg, ex);
                }
            }
        });
    }

    /**
     * 手动注册策略实例
     * <p>
     * 用于测试或非 Spring 环境，可以预先注册策略实例
     * </p>
     *
     * @param strategyClass 策略类
     * @param instance     策略实例
     * @throws IllegalArgumentException 如果参数为 null
     */
    public static void registerStrategy(Class<? extends FieldEncryptorStrategy> strategyClass,
                                        FieldEncryptorStrategy instance) {
        if (strategyClass == null) {
            throw new IllegalArgumentException("Strategy class cannot be null");
        }
        if (instance == null) {
            throw new IllegalArgumentException("Strategy instance cannot be null");
        }

        STRATEGY_CACHE.put(strategyClass, instance);
        log.debug("Registered strategy manually: {}", strategyClass.getName());
    }

    /**
     * 清空缓存
     * <p>
     * 主要用于测试或配置热更新场景
     * </p>
     */
    public static void clear() {
        int size = STRATEGY_CACHE.size();
        STRATEGY_CACHE.clear();
        log.info("Strategy cache cleared, removed {} entries", size);
    }

    /**
     * 获取缓存大小
     *
     * @return 缓存中策略实例的数量
     */
    public static int size() {
        return STRATEGY_CACHE.size();
    }

    /**
     * 检查缓存是否包含指定的策略类
     *
     * @param strategyClass 策略类
     * @return true 如果缓存中包含该策略实例
     */
    public static boolean contains(Class<? extends FieldEncryptorStrategy> strategyClass) {
        return strategyClass != null && STRATEGY_CACHE.containsKey(strategyClass);
    }

    /**
     * 移除指定的策略缓存
     *
     * @param strategyClass 策略类
     * @return 被移除的策略实例，如果不存在则返回 null
     */
    public static FieldEncryptorStrategy remove(Class<? extends FieldEncryptorStrategy> strategyClass) {
        if (strategyClass == null) {
            return null;
        }
        FieldEncryptorStrategy removed = STRATEGY_CACHE.remove(strategyClass);
        if (removed != null) {
            log.debug("Removed strategy from cache: {}", strategyClass.getName());
        }
        return removed;
    }
}

