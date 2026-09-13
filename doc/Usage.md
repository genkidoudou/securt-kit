# 使用指南

面向集成后的日常配置与扩展。接入步骤见 [快速开始](Quick-Start)。

## 基础配置

### 最小示例

```yaml
securtkit:
  encryptor:
    enable: true
    tables:
      - table-name: user
        fields:
          - field-name: name
          - field-name: phone
```

### 常用完整项

```yaml
securtkit:
  encryptor:
    enable: true
    mode: JDBC                    # JDBC | MYBATIS | OFF
    failure-policy: FALLBACK
    sql-parse-cache:
      enable: true
      max-size: 1000
    tables:
      - table-name: user
        datasource-id: primary    # 多数据源时填写
        fields:
          - field-name: name
          - field-name: phone
            strategy: com.example.PhoneEncryptorStrategy
```

## 通道模式

| mode | 适用 | 注意 |
|------|------|------|
| `JDBC` | 通用 JDBC | 驱动换拦截驱动，URL 使用 `jdbc:interceptor:` |
| `MYBATIS` | MyBatis / MP 为主 | 保持原驱动；勿与 JDBC 同开 |
| `OFF` | 临时关闭通道 | 配置仍可存在但不拦截 |

## 自定义加密策略

实现 `FieldEncryptorStrategy`（`encryption` / `decryption` 互逆，正确处理 `null`，建议线程安全），注册为 Spring Bean 或可被加载的类，并在字段上指定全限定名：

```yaml
securtkit:
  encryptor:
    tables:
      - table-name: user
        fields:
          - field-name: phone
            strategy: com.example.AESFieldEncryptorStrategy
```

未指定 `strategy` 时使用容器中的默认策略。

## 字段完整性摘要（Digest）

按源字段**明文**计算摘要写入目标列；可选读取时验签。

```yaml
securtkit:
  encryptor:
    digest-strategy: io.github.genkidoudou.core.strategy.HmacSha256DigestStrategy
    digest-hmac-key: ${DIGEST_HMAC_KEY}
    digest-partial-update: RELOAD
    digest-verify-on-read: false
    tables:
      - table-name: user
        fields:
          - field-name: phone
        digest:
          - source-fields: [phone]
            target-field: row_digest
```

表需预先有摘要列。密钥用环境变量 / 配置中心注入，勿提交仓库。运维验签可用 [Monitor](Monitor)「验签」页。

## LIKE

加密列 `LIKE` 默认由 `ExactMatchLikePatternHandler` 处理：无 `%`/`_` 时按精确匹配语义加密后再比；含通配符时通常无法按明文模糊语义命中密文。可实现 `LikePatternHandler` 扩展。

## 失败处理策略

| 策略 | 行为 |
|------|------|
| `FALLBACK`（默认） | 失败时用原值继续，偏稳定 |
| `FAIL_FAST` | 立即抛错，偏排障 |
| `RETRY` | 当前实现偏降级 |
| `SKIP` | 跳过该字段（可能为 null） |

## 性能

开启 `sql-parse-cache`，按 SQL 种类调整 `max-size`（常见 500～5000）。

## SECURT_SKIP

```yaml
securtkit:
  encryptor:
    skip-comment:
      enable: true
      token: SECURT_SKIP
```

SQL 含 `/* SECURT_SKIP */` 时可跳过自动加解密，便于运维旁路读密文或手工写密文条件。Playground / Monitor 部分工具依赖此能力。

## 最佳实践（摘要）

- 密钥与 Monitor 口令走密钥管理，不落代码库  
- 生产优先 `FALLBACK`，联调可用 `FAIL_FAST`  
- 同一数据源只选一种 mode  
- 历史明文刷密文用 Monitor 刷数：**先预览再回写**  

更多多数据源细节见 [Multi-Datasource](Multi-Datasource)。
