# 多数据源配置指南

## 概述

Securt-Kit 支持在多数据源场景下为不同的数据源配置不同的加密规则。这对于需要区分不同数据源加密策略的场景非常有用。

## 配置步骤

### 步骤 1: 配置数据源

使用 dynamic-datasource 或其他多数据源框架：

```yaml
spring:
  datasource:
    dynamic:
      primary: primary
      datasource:
        primary:
          driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
          url: jdbc:interceptor:h2:mem:primary_db;datasource-id=primary
        secondary:
          driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
          url: jdbc:interceptor:h2:mem:secondary_db;datasource-id=secondary
```

**关键点**：
- URL 必须包含 `datasource-id` 参数
- 使用 `jdbc:interceptor:` 前缀

### 步骤 2: 配置加密规则

```yaml
securtkit:
  encryptor:
    enable: true
    tables:
      # 主数据源：加密 name 和 phone
      - table-name: user
        datasource-id: primary
        fields:
          - field-name: name
          - field-name: phone
      # 从数据源：只加密 email
      - table-name: user
        datasource-id: secondary
        fields:
          - field-name: email
```

## 配置示例

### 示例 1: 差异化加密

不同数据源对同一张表使用不同的加密字段：

```yaml
securtkit:
  encryptor:
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
          - field-name: address
```

### 示例 2: 部分数据源加密

某些数据源加密，某些不加密：

```yaml
securtkit:
  encryptor:
    tables:
      - table-name: user
        datasource-id: primary
        fields:
          - field-name: name
      # secondary 数据源不配置，表示不加密
```

### 示例 3: 多表多数据源

```yaml
securtkit:
  encryptor:
    tables:
      - table-name: user
        datasource-id: primary
        fields:
          - field-name: name
      - table-name: order
        datasource-id: primary
        fields:
          - field-name: customer_name
      - table-name: payment
        datasource-id: secondary
        fields:
          - field-name: card_number
```

## 使用示例

### 代码中使用

```java
@Service
public class UserService {
    
    @Autowired
    private UserMapper userMapper;
    
    @DS("primary")  // 使用主数据源
    public void createUserInPrimary(User user) {
        // name 和 phone 字段会自动加密
        userMapper.insert(user);
    }
    
    @DS("secondary")  // 使用从数据源
    public void createUserInSecondary(User user) {
        // 只有 email 字段会加密
        userMapper.insert(user);
    }
}
```

## 注意事项

1. **URL 参数必须包含 `datasource-id`**
2. **表配置中必须指定 `datasource-id`**
3. **数据源标识必须一致**（URL 中的 `datasource-id` 和配置中的 `datasource-id`）
4. **单数据源场景可以不指定 `datasource-id`**

## 故障排查

### 问题：多数据源加密不生效

**检查项**：
1. URL 中是否包含 `datasource-id` 参数
2. 配置中是否指定了 `datasource-id`
3. 数据源标识是否匹配

### 问题：加密字段错误

**检查项**：
1. 确认当前使用的数据源
2. 检查该数据源的加密配置
3. 验证表名和字段名是否匹配

## 最佳实践

1. **明确标识**：为每个数据源使用清晰的标识
2. **统一管理**：在配置文件中统一管理所有数据源的加密规则
3. **文档记录**：记录每个数据源的加密策略
4. **测试验证**：充分测试多数据源场景

## 相关文档

- [使用指南](USAGE.md)
- [快速开始](QUICK-START.md)
- [测试项目](../securt-kit-dy-datasource-test-boot2/README.md)

