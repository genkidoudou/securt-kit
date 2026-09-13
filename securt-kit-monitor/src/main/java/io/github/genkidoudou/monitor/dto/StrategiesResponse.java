package io.github.genkidoudou.monitor.dto;

import lombok.Data;

import java.util.Map;

/**
 * 策略列表响应
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
public class StrategiesResponse {
    /**
     * 默认策略类名
     */
    private String defaultStrategy;

    /**
     * 表策略映射
     * key: 表名
     * value: 字段策略映射 (fieldName -> strategyClassName)
     */
    private Map<String, Map<String, String>> tableStrategies;
}

