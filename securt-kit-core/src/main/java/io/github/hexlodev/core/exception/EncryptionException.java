package io.github.hexlodev.core.exception;

/**
 * 加密异常
 * <p>
 * 当字段加密操作失败时抛出此异常。
 * 包含详细的上下文信息：表名、字段名、原始值等。
 * </p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
public class EncryptionException extends SecurtKitException {
    
    private final String tableName;
    private final String fieldName;
    private final String originalValue;

    public EncryptionException(String message, Throwable cause, 
                               String tableName, String fieldName, 
                               String originalValue) {
        super(message, cause);
        this.tableName = tableName;
        this.fieldName = fieldName;
        this.originalValue = originalValue;
    }

    public EncryptionException(String message, String tableName, String fieldName, String originalValue) {
        super(message);
        this.tableName = tableName;
        this.fieldName = fieldName;
        this.originalValue = originalValue;
    }

    public String getTableName() {
        return tableName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getOriginalValue() {
        return originalValue;
    }

    @Override
    public String toString() {
        return String.format("EncryptionException{table='%s', field='%s', message='%s'}", 
                tableName, fieldName, getMessage());
    }
}

