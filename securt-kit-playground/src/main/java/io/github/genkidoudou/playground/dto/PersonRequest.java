package io.github.genkidoudou.playground.dto;

import lombok.Data;

/**
 * Playground 人员表 CRUD 请求。
 */
@Data
public class PersonRequest {
    private String datasourceId;
    /** business | raw */
    private String view;
    private Long id;
    private String name;
    private String phone;
    private String idCard;
    private Integer age;
}
