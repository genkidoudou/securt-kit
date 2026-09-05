package io.github.hexlodev.core.exception;

/**
 * 摘要验签失败异常。
 */
public class DigestMismatchException extends SecurtKitException {

    public DigestMismatchException(String message) {
        super(message);
    }

    public DigestMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
