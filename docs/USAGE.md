# 使用指南

## 目录

- [基础配置](#基础配置)
- [多数据源配置](#多数据源配置)
- [自定义加密策略](#自定义加密策略)
- [字段完整性摘要](#字段完整性摘要)
- [失败处理策略](#失败处理策略)
- [性能优化](#性能优化)
- [最佳实践](#最佳实践)

---

## 基础配置

### 最小配置

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

### 完整配置

```yaml
securtkit:
  encryptor:
    enable: true                          # 是否启用加密功能
    failure-policy: FALLBACK              # 失败处理策略
    sql-parse-cache:                      # SQL 解析缓存配置
      enable: true                        # 是否启用缓存
      max-size: 1000                      # 缓存最大容量
    tables:                               # 表配置列表
      - table-name: user                  # 表名
        datasource-id: primary            # 数据源标识（可选）
        fields:                           # 字段配置列表
          - field-name: name              # 字段名
            strategy: com.example.MyStrategy  # 自定义策略（可选）
          - field-name: phone
          - field-name: email
```

---

## 多数据源配置

### 场景说明

在多数据源场景下，可以为不同的数据源配置不同的加密规则。

### 配置示例

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
    tables:
      # 主数据源配置
      - table-name: user
        datasource-id: primary
        fields:
          - field-name: name
          - field-name: phone
      # 从数据源配置
      - table-name: user
        datasource-id: secondary
        fields:
          - field-name: email
          - field-name: address
```

### 关键点

1. **数据源 URL 必须包含 `datasource-id` 参数**
2. **表配置中必须指定 `datasource-id`**
3. **不同数据源可以有不同的加密字段配置**

---

## 自定义加密策略

### 实现策略接口

```java
@Component
public class AESFieldEncryptorStrategy implements FieldEncryptorStrategy {
    
    private static final String KEY = "your-secret-key";
    private static final String ALGORITHM = "AES";
    
    @Override
    public String encryption(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(KEY.getBytes(), ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }
    
    @Override
    public String decryption(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(KEY.getBytes(), ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.decrypt(Base64.getDecoder().decode(cipherText));
            return new String(decrypted);
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }
}
```

### 配置使用

```yaml
securtkit:
  encryptor:
    tables:
      - table-name: user
        fields:
          - field-name: phone
            strategy: com.example.AESFieldEncryptorStrategy
```

### 策略要求

1. **必须实现 `FieldEncryptorStrategy` 接口**
2. **加密和解密必须互逆**
3. **必须处理 null 值**
4. **建议实现为线程安全的**

---

## 字段完整性摘要

摘要按源字段明文计算并写入目标列，可选在读取时验签以检测库侧篡改。HMAC 密钥应通过环境变量或配置中心注入，不要写入代码或提交到仓库。

```yaml
securtkit:
  encryptor:
    digest-strategy: io.github.genkidoudou.core.strategy.HmacSha256DigestStrategy
    digest-hmac-key: ${DIGEST_HMAC_KEY}
    digest-partial-update: RELOAD
    digest-verify-on-read: false
    # digest-failure-policy: FAIL_FAST
    tables:
      - table-name: user
        fields:
          - field-name: phone
        digest:
          - source-fields: [phone]
            target-field: row_digest
```

数据库表需要预先创建 `row_digest` 列。`RELOAD` 表示部分更新缺少摘要源字段时从当前行补读；开启 `digest-verify-on-read` 后，验签失败按 `digest-failure-policy`（未配置则按 `failure-policy`）处理。

---

## 失败处理策略

框架支持 4 种失败处理策略：

### FALLBACK（默认，推荐）

加密/解密失败时，使用原始值继续执行，不中断操作。

```yaml
securtkit:
  encryptor:
    failure-policy: FALLBACK
```

**适用场景**：生产环境，保证系统稳定性。

### FAIL_FAST

加密/解密失败时，立即抛出异常，中断操作。

```yaml
securtkit:
  encryptor:
    failure-policy: FAIL_FAST
```

**适用场景**：开发/测试环境，快速发现问题。

### RETRY

加密/解密失败时，尝试重试（当前实现为降级处理）。

```yaml
securtkit:
  encryptor:
    failure-policy: RETRY
```

### SKIP

加密/解密失败时，跳过该字段，返回 null。

```yaml
securtkit:
  encryptor:
    failure-policy: SKIP
```

---

## 性能优化

### SQL 解析缓存

启用 SQL 解析缓存可以显著提升性能：

```yaml
securtkit:
  encryptor:
    sql-parse-cache:
      enable: true
      max-size: 1000  # 根据应用实际 SQL 数量调整
```

**建议值**：
- 小型应用：500-1000
- 中型应用：1000-2000
- 大型应用：2000-5000

### 缓存调优

- 监控缓存命中率
- 根据实际 SQL 数量调整 `max-size`
- 定期清理缓存（如果需要）

---

## 最佳实践

### 1. 配置管理

- ✅ 使用配置文件管理加密规则
- ✅ 不同环境使用不同的配置文件
- ✅ 敏感信息（如密钥）使用环境变量或配置中心

### 2. 加密策略

- ✅ 使用标准加密算法（AES、RSA 等）
- ✅ 密钥管理要安全（不要硬编码）
- ✅ 定期轮换密钥

### 3. 性能优化

- ✅ 启用 SQL 解析缓存
- ✅ 合理设置缓存大小
- ✅ 监控性能指标

### 4. 错误处理

- ✅ 生产环境使用 FALLBACK 策略
- ✅ 记录详细的错误日志
- ✅ 设置告警机制

### 5. 测试

- ✅ 充分测试加密解密功能
- ✅ 测试异常场景
- ✅ 性能测试

---

## 常见配置场景

### 场景 1: 单表多字段

```yaml
securtkit:
  encryptor:
    tables:
      - table-name: user
        fields:
          - field-name: name
          - field-name: phone
          - field-name: email
          - field-name: id_card
```

### 场景 2: 多表配置

```yaml
securtkit:
  encryptor:
    tables:
      - table-name: user
        fields:
          - field-name: name
          - field-name: phone
      - table-name: order
        fields:
          - field-name: customer_name
          - field-name: customer_phone
      - table-name: payment
        fields:
          - field-name: card_number
```

### 场景 3: 字段级策略

```yaml
securtkit:
  encryptor:
    tables:
      - table-name: user
        fields:
          - field-name: phone
            strategy: com.example.PhoneEncryptorStrategy
          - field-name: email
            strategy: com.example.EmailEncryptorStrategy
```

---

## 故障排查

### 问题 1: 加密不生效

**检查项**：
1. 数据源 URL 是否使用 `jdbc:interceptor:` 前缀
2. 配置是否正确加载
3. 表名和字段名是否匹配（区分大小写）

### 问题 2: 性能问题

**检查项**：
1. 是否启用了 SQL 解析缓存
2. 缓存大小是否合理
3. 是否有大量未缓存的 SQL

### 问题 3: 解密失败

**检查项**：
1. 加密策略是否正确实现
2. 密钥是否一致
3. 数据是否被其他方式修改

---

## 更多资源

- [API 文档](API.md)
- [架构设计](ARCHITECTURE.md)
- [测试示例](../securt-kit-test-boot2/README.md)

