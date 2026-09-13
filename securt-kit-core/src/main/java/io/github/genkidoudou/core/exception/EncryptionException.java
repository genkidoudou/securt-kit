package io.github.genkidoudou.core.exception;

/**
 * 加密异常
 * 
 * <p>当字段加密操作失败时抛出此异常。
 * 包含详细的上下文信息：表名、字段名、原始值等，便于问题排查和日志记录。</p>
 * 
 * <p>异常信息：</p>
 * <ul>
 *   <li>tableName: 发生加密失败的表名</li>
 *   <li>fieldName: 发生加密失败的字段名</li>
 *   <li>originalValue: 原始值（明文），注意：可能包含敏感信息，生产环境应脱敏</li>
 * </ul>
 * 
 * <p>使用场景：</p>
 * <ul>
 *   <li>加密策略实现抛出异常时</li>
 *   <li>加密密钥无效或过期时</li>
 *   <li>加密算法初始化失败时</li>
 *   <li>当失败策略设置为 FAIL_FAST 时</li>
 * </ul>
 * 
 * <p>异常处理：</p>
 * <pre>{@code
 * try {
 *     String encrypted = strategy.encryption(value);
 * } catch (EncryptionException e) {
 *     log.error("加密失败 [table={}, field={}]", 
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
public class EncryptionException extends SecurtKitException {
    
    /**
     * 表名
     */
    private final String tableName;
    
    /**
     * 字段名
     */
    private final String fieldName;
    
    /**
     * 原始值（明文）
     * 
     * <p>注意：此字段可能包含敏感信息，在日志记录时应该脱敏处理。</p>
     */
    private final String originalValue;

    /**
     * 构造函数
     * 
     * @param message 异常消息
     * @param cause 导致此异常的异常
     * @param tableName 表名
     * @param fieldName 字段名
     * @param originalValue 原始值（明文）
     */
    public EncryptionException(String message, Throwable cause, 
                               String tableName, String fieldName, 
                               String originalValue) {
        super(message, cause);
        this.tableName = tableName;
        this.fieldName = fieldName;
        this.originalValue = originalValue;
    }

    /**
     * 构造函数
     * 
     * @param message 异常消息
     * @param tableName 表名
     * @param fieldName 字段名
     * @param originalValue 原始值（明文）
     */
    public EncryptionException(String message, String tableName, String fieldName, String originalValue) {
        super(message);
        this.tableName = tableName;
        this.fieldName = fieldName;
        this.originalValue = originalValue;
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
     * 获取原始值（明文）
     * 
     * <p>注意：此值可能包含敏感信息，在生产环境日志中应该脱敏处理。</p>
     * 
     * @return 原始值（明文）
     */
    public String getOriginalValue() {
        return originalValue;
    }

    /**
     * 返回异常的字符串表示
     * 
     * <p>不包含原始值，避免敏感信息泄露。</p>
     * 
     * @return 异常的字符串表示
     */
    @Override
    public String toString() {
        return String.format("EncryptionException{table='%s', field='%s', message='%s'}", 
                tableName, fieldName, getMessage());
    }
}

