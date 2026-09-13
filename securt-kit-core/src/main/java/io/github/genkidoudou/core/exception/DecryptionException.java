package io.github.genkidoudou.core.exception;

/**
 * 解密异常
 * 
 * <p>当字段解密操作失败时抛出此异常。
 * 包含详细的上下文信息：表名、字段名、加密值等，便于问题排查和日志记录。</p>
 * 
 * <p>异常信息：</p>
 * <ul>
 *   <li>tableName: 发生解密失败的表名</li>
 *   <li>fieldName: 发生解密失败的字段名</li>
 *   <li>encryptedValue: 加密后的值（密文），注意：通常不包含敏感信息</li>
 * </ul>
 * 
 * <p>使用场景：</p>
 * <ul>
 *   <li>解密策略实现抛出异常时</li>
 *   <li>加密密钥无效或过期时</li>
 *   <li>密文格式错误或损坏时</li>
 *   <li>当失败策略设置为 FAIL_FAST 时</li>
 * </ul>
 * 
 * <p>异常处理：</p>
 * <pre>{@code
 * try {
 *     String decrypted = strategy.decryption(encryptedValue);
 * } catch (DecryptionException e) {
 *     log.error("解密失败 [table={}, field={}]", 
 *             e.getTableName(), e.getFieldName(), e);
 *     // 根据失败策略处理：FAIL_FAST 抛出异常，FALLBACK 返回原值
 * }
 * }</pre>
 *
 * @author hexlodev
 * @since 1.0.0
 * @see SecurtKitException
 * @see EncryptionHandler
 */
public class DecryptionException extends SecurtKitException {
    
    /**
     * 表名
     */
    private final String tableName;
    
    /**
     * 字段名
     */
    private final String fieldName;
    
    /**
     * 加密后的值（密文）
     */
    private final String encryptedValue;

    /**
     * 构造函数
     * 
     * @param message 异常消息
     * @param cause 导致此异常的异常
     * @param tableName 表名
     * @param fieldName 字段名
     * @param encryptedValue 加密后的值（密文）
     */
    public DecryptionException(String message, Throwable cause, 
                               String tableName, String fieldName, 
                               String encryptedValue) {
        super(message, cause);
        this.tableName = tableName;
        this.fieldName = fieldName;
        this.encryptedValue = encryptedValue;
    }

    /**
     * 构造函数
     * 
     * @param message 异常消息
     * @param tableName 表名
     * @param fieldName 字段名
     * @param encryptedValue 加密后的值（密文）
     */
    public DecryptionException(String message, String tableName, String fieldName, String encryptedValue) {
        super(message);
        this.tableName = tableName;
        this.fieldName = fieldName;
        this.encryptedValue = encryptedValue;
    }

    /**
     * 获取表名
     * 
     * @return 表名
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * 获取字段名
     * 
     * @return 字段名
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * 获取加密后的值（密文）
     * 
     * @return 加密后的值（密文）
     */
    public String getEncryptedValue() {
        return encryptedValue;
    }

    /**
     * 返回异常的字符串表示
     * 
     * @return 异常的字符串表示
     */
    @Override
    public String toString() {
        return String.format("DecryptionException{table='%s', field='%s', message='%s'}", 
                tableName, fieldName, getMessage());
    }
}

