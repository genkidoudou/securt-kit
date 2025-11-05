# Securt-Kit 代码注释规范

> 本文档定义了 Securt-Kit 项目的代码注释规范和最佳实践。

## 📋 目录

- [注释规范](#注释规范)
- [JavaDoc 注释](#javadoc-注释)
- [行内注释](#行内注释)
- [注释示例](#注释示例)
- [注释检查清单](#注释检查清单)

---

## 📝 注释规范

### 基本原则

1. **所有公共类和方法必须有 JavaDoc 注释**
2. **复杂逻辑必须有行内注释**
3. **注释应该清晰、准确、完整**
4. **注释应该与代码同步更新**
5. **避免无意义的注释**

### 注释语言

- **类和方法注释**：使用中文
- **参数和返回值注释**：使用中文
- **行内注释**：使用中文
- **示例代码**：代码使用英文，注释使用中文

---

## 📚 JavaDoc 注释

### 类注释

每个公共类都应该有完整的 JavaDoc 注释，包含：

```java
/**
 * 类的简短描述（一句话）
 * 
 * <p>详细描述（可选，如果简短描述不够清晰）</p>
 * 
 * <p>主要功能：</p>
 * <ul>
 *   <li>功能点1</li>
 *   <li>功能点2</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * // 示例代码
 * }</pre>
 * 
 * @author 作者名
 * @since 版本号
 * @see 相关类
 */
public class MyClass {
}
```

### 方法注释

每个公共方法都应该有完整的 JavaDoc 注释：

```java
/**
 * 方法的简短描述（一句话）
 * 
 * <p>详细描述（可选）</p>
 * 
 * <p>方法执行流程：</p>
 * <ol>
 *   <li>步骤1</li>
 *   <li>步骤2</li>
 * </ol>
 * 
 * <p>性能说明：</p>
 * <ul>
 *   <li>时间复杂度：O(n)</li>
 *   <li>空间复杂度：O(1)</li>
 * </ul>
 *
 * @param parameterName 参数说明
 * @return 返回值说明
 * @throws ExceptionType 异常说明
 * @since 版本号
 * @see 相关方法或类
 */
public String methodName(String parameterName) throws Exception {
}
```

### 字段注释

重要的字段应该有注释：

```java
/**
 * 字段说明
 * 
 * <p>用途和约束说明</p>
 */
private String fieldName;
```

---

## 💬 行内注释

### 何时使用行内注释

1. **复杂算法**：解释算法逻辑
2. **业务逻辑**：解释业务规则
3. **性能优化**：说明优化原因
4. **临时方案**：说明为什么使用临时方案
5. **TODO/FIXME**：标记待办事项

### 行内注释格式

```java
// 单行注释：简短说明
if (condition) {
    // 这里是关键逻辑，需要特殊处理
    doSomething();
}

/*
 * 多行注释：详细说明
 * 用于复杂的逻辑解释
 */
```

---

## 📖 注释示例

### 示例1：接口注释

```java
/**
 * 字段加密解密策略接口
 * 
 * <p>该接口定义了字段加密和解密的核心方法，所有加密策略实现都必须实现此接口。
 * 通过策略模式，允许用户为不同的字段配置不同的加密算法和密钥。</p>
 * 
 * <p>主要功能：</p>
 * <ul>
 *   <li>提供统一的加密和解密方法</li>
 *   <li>支持自定义加密算法实现</li>
 *   <li>支持字段级别的加密策略配置</li>
 * </ul>
 * 
 * <p>实现要求：</p>
 * <ul>
 *   <li>加密和解密方法必须互逆（加密后再解密应得到原始值）</li>
 *   <li>必须处理 null 值（通常返回 null）</li>
 *   <li>应该处理异常情况，避免抛出未检查异常</li>
 *   <li>建议实现为线程安全的</li>
 * </ul>
 * 
 * @author hexlodev
 * @since 1.0.0
 * @see StrategyCache
 * @see EncryptionHandler
 */
public interface FieldEncryptorStrategy {
}
```

### 示例2：方法注释

```java
/**
 * 构建参数索引到字段信息的映射
 * 
 * <p>将 O(n) 的 Stream 查找优化为 O(1) 的 Map 查找，提升性能。
 * 在构造函数中调用一次，后续所有 setString 调用都使用此映射。</p>
 *
 * @param pair SQL解析结果对，包含占位符映射和字段信息
 * @return 参数索引到字段信息的映射，如果不需要加密则返回空Map
 * @throws IllegalArgumentException 如果 pair 为 null
 * @since 1.0.0
 */
private Map<Integer, ColumnTableDto> buildParameterIndexMap(
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair) {
    // 实现逻辑
}
```

### 示例3：复杂逻辑注释

```java
/**
 * 设置字符串参数 - 拦截方法
 *
 * <p>如果该参数对应需要加密的字段，则会在设置前进行加密处理。
 * 加密后的值会被缓存，用于后续的SQL日志输出。</p>
 *
 * <p>性能优化：使用预构建的参数索引映射（O(1)查找）替代 Stream 遍历（O(n)查找），
 * 在高频调用场景下显著提升性能。</p>
 *
 * @param parameterIndex 参数索引，从1开始
 * @param x              参数值
 * @throws SQLException 如果设置参数失败
 */
@Override
public void setString(int parameterIndex, String x) throws SQLException {
    String newValue = x;

    // 使用预构建的映射进行 O(1) 查找，替代原来的 O(n) Stream 查找
    if (SecurtkitUtils.needEncrypt(this.tables) && this.parameterIndexToFieldMap != null) {
        ColumnTableDto columnTableDto = this.parameterIndexToFieldMap.get(parameterIndex);
        
        if (columnTableDto != null) {
            // 查找加密策略并执行加密
            // ...
        }
    }

    delegate.setString(parameterIndex, newValue);
    parameterValues.put(parameterIndex, newValue);
}
```

---

## ✅ 注释检查清单

### 类注释检查

- [ ] 类有简短描述
- [ ] 类有详细描述（如需要）
- [ ] 列出了主要功能点
- [ ] 提供了使用示例（如需要）
- [ ] 标注了 @author 和 @since
- [ ] 添加了 @see 引用（如需要）

### 方法注释检查

- [ ] 方法有简短描述
- [ ] 方法有详细描述（如需要）
- [ ] 所有参数都有 @param 注释
- [ ] 返回值有 @return 注释
- [ ] 所有异常都有 @throws 注释
- [ ] 标注了 @since 版本号
- [ ] 添加了 @see 引用（如需要）
- [ ] 复杂逻辑有性能说明

### 字段注释检查

- [ ] 重要的公共字段有注释
- [ ] 复杂的私有字段有注释
- [ ] 常量有注释说明用途

### 代码质量检查

- [ ] 没有过时的注释
- [ ] 注释与代码保持一致
- [ ] 没有无意义的注释
- [ ] TODO/FIXME 都有说明

---

## 🎯 注释优先级

### 高优先级（必须）

1. ✅ 所有公共接口和类
2. ✅ 所有公共方法
3. ✅ 复杂的算法和业务逻辑
4. ✅ 性能优化相关的代码

### 中优先级（建议）

1. ⚠️ 重要的私有方法
2. ⚠️ 重要的字段
3. ⚠️ 配置类和方法

### 低优先级（可选）

1. ⚪ 简单的 getter/setter（如果使用 Lombok 可以省略）
2. ⚪ 自解释的代码

---

## 📚 相关文档

- [ARCHITECTURE.md](./ARCHITECTURE.md) - 架构文档
- [CONTRIBUTING.md](./CONTRIBUTING.md) - 贡献指南
- [OPTIMIZATION-ANALYSIS.md](./OPTIMIZATION-ANALYSIS.md) - 优化分析

---

## 📌 总结

良好的注释可以：

- ✅ **提升代码可读性**：帮助开发者快速理解代码意图
- ✅ **降低维护成本**：减少理解代码的时间
- ✅ **便于知识传承**：新团队成员可以快速上手
- ✅ **提升代码质量**：编写注释时能发现代码问题

**记住**：注释是代码的一部分，应该与代码一样被认真对待！

---

**文档版本**：v1.0  
**创建日期**：2025-01-XX  
**最后更新**：2025-01-XX

