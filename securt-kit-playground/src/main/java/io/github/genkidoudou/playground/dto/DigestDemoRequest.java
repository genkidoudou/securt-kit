package io.github.genkidoudou.playground.dto;

import lombok.Data;

/**
 * Digest 演示请求
 *
 * <p>action: insert | update-phone | tamper | verify-read | raw-query</p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
public class DigestDemoRequest {
    private String datasourceId;
    private String action;
    private Long id;
    private String name;
    private String phone;
    private Integer age;
    private String email;
    /** tamper 时写入的伪造摘要 */
    private String fakeDigest;
}
