package io.github.hexlodev.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 字段加密配置属性：
 * prefix: securtkit.encryptor
 * - enable：是否开启（预留）
 * - tables：按表配置需要加密的字段及策略，未指定策略则回落到默认策略
 */
@ConfigurationProperties(prefix = FieldEncryptorProperties.PREFIX)
@Data
public class FieldEncryptorProperties {

    public static final String PREFIX = "securtkit.encryptor";
    /**
     * 是否开启
     * @since 2025/10/7
     */

    private boolean enable;

    /**
     * 表配置
     * @since 2025/10/8
     */

    private List<TableConfig> tables;


    /**
     * 表配置项：tableName + fields
     */
    /**
     * 表配置
     * @author luyanan
     * @since 2025/10/8
     */
    @Data
    public static class TableConfig {


        /**
         * 表名
         * @since 2025/10/8
         */

        @NotBlank(message = "表名不能为空")
        private String tableName;


        /**
         * 字段配置列表：fieldName + strategy（可选）
         */
        private List<FieldConfig> fields;

    }


    /**
     * 字段配置：字段名与可选策略类名（全限定名）
     */
    /**
     * 字段配置
     * @since 2025/10/8
     */

    @Data
    public static class FieldConfig {

        /**
         * 字段名称
         * @since 2025/10/8
         */

        private String fieldName;
        /**
         * 策略类名（全限定名）；为空则使用默认策略
         */
        /**
         * 字段加密策略(不配置使用默认的策略)
         * @since 2025/10/8
         */

        private String strategy;
    }

}
