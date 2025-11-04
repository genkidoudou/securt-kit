package io.github.hexlodev.core.exception;

/**
 * Securt-Kit 基础异常类
 * <p>
 * 所有 Securt-Kit 相关异常的基类，提供统一的异常处理入口。
 * </p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
public class SecurtKitException extends RuntimeException {

    public SecurtKitException(String message) {
        super(message);
    }

    public SecurtKitException(String message, Throwable cause) {
        super(message, cause);
    }

    public SecurtKitException(Throwable cause) {
        super(cause);
    }
}

