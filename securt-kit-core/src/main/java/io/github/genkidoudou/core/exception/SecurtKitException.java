package io.github.genkidoudou.core.exception;

/**
 * Securt-Kit 基础异常类
 * 
 * <p>所有 Securt-Kit 相关异常的基类，提供统一的异常处理入口。
 * 继承自 {@link RuntimeException}，作为非检查异常，不会强制调用者处理。</p>
 * 
 * <p>异常层次结构：</p>
 * <pre>
 * SecurtKitException (基类)
 *   ├── ConfigurationException (配置异常)
 *   ├── EncryptionException (加密异常)
 *   ├── DecryptionException (解密异常)
 *   └── SqlParseException (SQL解析异常)
 * </pre>
 * 
 * <p>使用建议：</p>
 * <ul>
 *   <li>业务代码应该捕获并处理这些异常</li>
 *   <li>在生产环境中，建议使用统一的异常处理器</li>
 *   <li>避免在异常消息中泄露敏感信息（如密钥、SQL语句等）</li>
 * </ul>
 * 
 * @author hexlodev
 * @since 1.0.0
 * @see ConfigurationException
 * @see EncryptionException
 * @see DecryptionException
 * @see SqlParseException
 */
public class SecurtKitException extends RuntimeException {

    /**
     * 构造函数
     * 
     * @param message 异常消息
     */
    public SecurtKitException(String message) {
        super(message);
    }

    /**
     * 构造函数
     * 
     * @param message 异常消息
     * @param cause   导致此异常的异常
     */
    public SecurtKitException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造函数
     * 
     * @param cause 导致此异常的异常
     */
    public SecurtKitException(Throwable cause) {
        super(cause);
    }
}

