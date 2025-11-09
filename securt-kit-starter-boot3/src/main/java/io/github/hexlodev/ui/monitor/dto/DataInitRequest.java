package io.github.hexlodev.ui.monitor.dto;

import io.github.hexlodev.ui.monitor.security.SafeInput;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 数据初始化请求
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
public class DataInitRequest {
    /**
     * 表名（必填）
     */
    @NotBlank(message = "表名不能为空")
    @SafeInput(maxLength = 64, pattern = "^[a-zA-Z_][a-zA-Z0-9_.]*$", message = "表名格式不正确")
    private String tableName;

    /**
     * WHERE条件（可选）
     */
    @SafeInput(maxLength = 1000, message = "WHERE条件过长")
    private String whereCondition;

    /**
     * 主键字段名（必填）
     */
    @NotBlank(message = "主键字段不能为空")
    @SafeInput(maxLength = 64, pattern = "^[a-zA-Z_][a-zA-Z0-9_]*$", message = "主键字段格式不正确")
    private String primaryKeyField;

    /**
     * 数据源标识（可选，多数据源场景使用）
     * 
     * <p>如果不指定，则使用默认数据源（"default"）</p>
     * <p>多数据源场景下，需要指定数据源标识以获取正确的加密配置</p>
     * 
     * @since 1.1.0
     */
    @SafeInput(maxLength = 64, pattern = "^[a-zA-Z_][a-zA-Z0-9_]*$", message = "数据源标识格式不正确")
    private String datasourceId;
}

