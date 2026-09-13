package io.github.genkidoudou.monitor.dto;

import lombok.Data;

import java.util.List;

/**
 * 数据源列表响应
 * 
 * <p>用于返回所有配置的数据源标识列表</p>
 * 
 * @author hexlodev
 * @since 1.1.0
 */
@Data
public class DatasourceListResponse {
    /**
     * 数据源标识列表
     */
    private List<String> datasourceIds;

    /**
     * 默认数据源标识
     */
    private String defaultDatasourceId;
}

