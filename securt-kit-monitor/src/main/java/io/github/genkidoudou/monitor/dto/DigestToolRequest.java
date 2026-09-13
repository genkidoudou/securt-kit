package io.github.genkidoudou.monitor.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DigestToolRequest {
    /** sign | verify */
    private String action;
    private String datasourceId;
    /**
     * 表名。为空时按全局 digest-strategy 对 {@link #plaintext}（或 sourceValues 中单值）做签名/验签。
     */
    private String tableName;
    /** 无表名时的纯文本内容（加密解密菜单签名/验签） */
    private String plaintext;
    /** 明文源字段 */
    private Map<String, String> sourceValues = new LinkedHashMap<>();
    /** verify 时期望摘要 */
    private String expectedDigest;
    private String targetField;
}
