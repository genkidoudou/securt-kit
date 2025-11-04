package io.github.hexlodev.core.exception;

/**
 * 解密异常
 * <p>
 * 当字段解密操作失败时抛出此异常。
 * 包含详细的上下文信息：表名、字段名等。
 * </p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
public class DecryptionException extends SecurtKitException {
    
    private final String tableName;
    private final String fieldName;
    private final String encryptedValue;

    public DecryptionException(String message, Throwable cause, 
                               String tableName, String fieldName, 
                               String encryptedValue) {
        super(message, cause);
        this.tableName = tableName;
        this.fieldName = fieldName;
        this.encryptedValue = encryptedValue;
    }

    public DecryptionException(String message, String tableName, String fieldName, String encryptedValue) {
        super(message);
        this.tableName = tableName;
        this.fieldName = fieldName;
        this.encryptedValue = encryptedValue;
    }

    public String getTableName() {
        return tableName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getEncryptedValue() {
        return encryptedValue;
    }

    @Override
    public String toString() {
        return String.format("DecryptionException{table='%s', field='%s', message='%s'}", 
                tableName, fieldName, getMessage());
    }
}

