# 多数据源

可为不同数据源配置不同加密字段或策略。同名表用 `datasource-id` 区分。

## JDBC 模式要点

1. 各数据源使用拦截驱动与 `jdbc:interceptor:` URL  
2. URL 带 `datasource-id=...`，与表规则中的 `datasource-id` **一致**

```yaml
spring:
  datasource:
    dynamic:
      primary: primary
      datasource:
        primary:
          driver-class-name: io.github.genkidoudou.core.interceptor.SimpleInterceptorDriver
          url: jdbc:interceptor:h2:mem:primary_db;datasource-id=primary
        secondary:
          driver-class-name: io.github.genkidoudou.core.interceptor.SimpleInterceptorDriver
          url: jdbc:interceptor:h2:mem:secondary_db;datasource-id=secondary

securtkit:
  encryptor:
    enable: true
    mode: JDBC
    tables:
      - table-name: user
        datasource-id: primary
        fields:
          - field-name: name
          - field-name: phone
      - table-name: user
        datasource-id: secondary
        fields:
          - field-name: email
```

## MYBATIS 模式

保持各数据源原驱动；表规则仍用 `datasource-id` 对齐动态数据源标识。同一数据源不要再挂 JDBC 拦截通道。

## 差异化示例

- **不同字段**：primary 加密 `phone`，archive 加密 `id_card`  
- **部分加密**：仅部分数据源配置 `tables`，其它源不拦截  

## 排障

| 现象 | 检查 |
|------|------|
| 某源未加密 | URL / 规则的 `datasource-id` 是否一致 |
| 两源串规则 | 是否漏写 `datasource-id` 导致落到默认 |
| 重复加解密 | 是否 JDBC+MYBATIS 同开 |

Monitor / Playground 顶栏可切换数据源（测试工程），切换后重新加载配置或预览。
