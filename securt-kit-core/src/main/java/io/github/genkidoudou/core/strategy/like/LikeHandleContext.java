package io.github.genkidoudou.core.strategy.like;

/**
 * LIKE 处理上下文
 *
 * @author hexlodev
 * @since 1.2.0
 */
public class LikeHandleContext {

    private final String tableName;
    private final String columnName;
    private final String datasourceId;
    private final int parameterIndex;

    public LikeHandleContext(String tableName, String columnName, String datasourceId, int parameterIndex) {
        this.tableName = tableName;
        this.columnName = columnName;
        this.datasourceId = datasourceId;
        this.parameterIndex = parameterIndex;
    }

    public String getTableName() {
        return tableName;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getDatasourceId() {
        return datasourceId;
    }

    public int getParameterIndex() {
        return parameterIndex;
    }
}
