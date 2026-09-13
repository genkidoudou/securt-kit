# LIKE 模式处理（LikePatternHandler）

## 背景

加密列上的 `LIKE` **不能**简单理解为「把模式串整体加密就能模糊查」。  
默认仅支持「无通配符」的精确匹配（语义等价于 `=`）。

## 接口

```java
public interface LikePatternHandler {
    LikeHandleResult handle(String pattern, LikeHandleContext context);
}
```

- `ENCRYPT`：用返回值再走字段 `FieldEncryptorStrategy.encryption`
- `SKIP`：不加密，参数原样下发（并打日志）
- `REJECT`：拒绝绑定（抛异常）

## 默认实现：ExactMatchLikePatternHandler

| 模式 | 行为 |
|------|------|
| `张三`（无 `%` `_`） | 加密该串，等价精确匹配 |
| `%张%` / `张_` | **跳过加密**并 warn；请自定义 Handler 做模糊方案 |

## 配置

```yaml
securtkit:
  encryptor:
    # 可选；不配则默认 ExactMatchLikePatternHandler
    like-pattern-handler: io.github.genkidoudou.core.strategy.like.ExactMatchLikePatternHandler
```

自定义示例：实现 `LikePatternHandler`，注册为 Spring Bean 或提供无参构造，再把全限定类名配到 `like-pattern-handler`。

## 链路

```text
WHERE name LIKE ?
  → 解析标记 ParameterMatchType.LIKE
  → ParameterEncryptor
  → LikePatternHandler.handle(pattern)
  → ENCRYPT 时再 FieldEncryptorStrategy.encryption
```
