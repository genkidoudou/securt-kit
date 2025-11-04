package io.github.hexlodev.core.exception;

/**
 * 配置异常
 * <p>
 * 当配置无效或配置相关操作失败时抛出此异常。
 * </p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
public class ConfigurationException extends SecurtKitException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}

