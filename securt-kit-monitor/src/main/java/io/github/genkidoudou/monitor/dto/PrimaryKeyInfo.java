package io.github.genkidoudou.monitor.dto;

import lombok.Data;

/**
 * 主键解析结果
 */
@Data
public class PrimaryKeyInfo {
    /** 主键列名 */
    private String column;
    /** config | tableInfo | manual | none */
    private String source;
}
