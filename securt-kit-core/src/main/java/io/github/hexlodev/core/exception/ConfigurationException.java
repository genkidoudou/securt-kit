package io.github.hexlodev.core.exception;

/**
 * 配置异常
 * 
 * <p>当配置无效或配置相关操作失败时抛出此异常。
 * 用于在应用启动时验证配置，或在运行时检测配置问题。</p>
 * 
 * <p>使用场景：</p>
 * <ul>
 *   <li>配置文件格式错误</li>
 *   <li>必需配置项缺失</li>
 *   <li>配置值格式不正确（如表名、字段名格式错误）</li>
 *   <li>策略类无法加载</li>
 *   <li>配置验证失败</li>
 * </ul>
 * 
 * <p>异常处理：</p>
 * <pre>{@code
 * try {
 *     TableCache.init(properties);
 * } catch (ConfigurationException e) {
 *     log.error("配置初始化失败: {}", e.getMessage(), e);
 *     // 应用启动失败，需要修复配置
 *     throw e;
 * }
 * }</pre>
 *
 * @author hexlodev
 * @since 1.0.0
 * @see SecurtKitException
 * @see TableCache
 */
public class ConfigurationException extends SecurtKitException {

    /**
     * 构造函数
     * 
     * @param message 异常消息，应该明确指出配置问题
     */
    public ConfigurationException(String message) {
        super(message);
    }

    /**
     * 构造函数
     * 
     * @param message 异常消息，应该明确指出配置问题
     * @param cause 导致此异常的异常
     */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}

