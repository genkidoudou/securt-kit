# Securt-Kit 贡献指南

感谢你对 Securt-Kit 项目的关注！本文档将帮助你了解如何为项目做出贡献。

## 📋 目录

- [行为准则](#行为准则)
- [如何贡献](#如何贡献)
- [开发环境](#开发环境)
- [代码规范](#代码规范)
- [提交规范](#提交规范)
- [测试要求](#测试要求)
- [文档要求](#文档要求)

---

## 🤝 行为准则

我们致力于为每个人提供友好、开放和包容的环境。所有贡献者都应遵守以下行为准则：

- **尊重他人**: 尊重不同的观点和经验
- **欢迎反馈**: 接受建设性的批评和建议
- **包容性**: 欢迎来自不同背景的贡献者

---

## 💡 如何贡献

### 贡献方式

1. **报告 Bug**
   - 提交详细的 Bug 报告
   - 包含重现步骤
   - 提供环境信息

2. **提出新功能**
   - 描述功能需求
   - 说明使用场景
   - 讨论实现方案

3. **改进文档**
   - 修复文档错误
   - 补充缺失内容
   - 改进文档可读性

4. **提交代码**
   - 修复 Bug
   - 实现新功能
   - 重构代码
   - 性能优化

---

## 🛠️ 开发环境

### 前置要求

- **JDK**: 1.8+
- **Maven**: 3.6+
- **IDE**: IntelliJ IDEA / Eclipse（推荐）

### 环境搭建

1. **克隆项目**

```bash
git clone https://github.com/hexlodev/securt-kit.git
cd securt-kit
```

2. **编译项目**

```bash
mvn clean install -DskipTests
```

3. **运行测试**

```bash
# 运行所有测试
mvn test

# 运行特定模块测试
cd securt-kit-core
mvn test

# 运行测试项目
cd securt-kit-test
mvn spring-boot:run
```

4. **IDE 导入**

使用 IntelliJ IDEA：
- File → Open → 选择项目根目录
- 等待 Maven 依赖下载
- 配置 JDK 1.8

---

## 📝 代码规范

### Java 代码规范

#### 1. 命名规范

```java
// 类名：大驼峰
public class SimpleInterceptorDriver { }

// 方法名：小驼峰
public void connect(String url) { }

// 常量名：全大写下划线分隔
private static final String JDBC_PREFIX = "jdbc:interceptor:";

// 私有字段：小驼峰
private final Connection delegate;
```

#### 2. 注释规范

```java
/**
 * 类的简短描述
 *
 * <p>详细描述</p>
 *
 * @author 作者名
 * @since 版本号
 */
public class MyClass {
    
    /**
     * 方法的简短描述
     *
     * @param param 参数说明
     * @return 返回值说明
     * @throws Exception 异常说明
     */
    public String method(String param) throws Exception {
        // 方法实现
    }
}
```

#### 3. 日志规范

使用 SLF4J 和 `@Slf4j` 注解：

```java
@Slf4j
public class MyClass {
    public void method() {
        log.info("操作成功");
        log.debug("调试信息: {}", value);
        log.error("错误信息", exception);
    }
}
```

#### 4. 异常处理

```java
// 正确：提供有意义的异常信息
try {
    // 操作
} catch (SQLException e) {
    log.error("数据库操作失败: {}", sql, e);
    throw new RuntimeException("数据库操作失败", e);
}

// 错误：吞掉异常
try {
    // 操作
} catch (Exception e) {
    // 什么也不做
}
```

---

## 📤 提交规范

### Git Commit 规范

使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

```
<type>(<scope>): <subject>

<body>

<footer>
```

#### Type 类型

- `feat`: 新功能
- `fix`: 修复 Bug
- `docs`: 文档更新
- `style`: 代码格式（不影响功能）
- `refactor`: 重构
- `perf`: 性能优化
- `test`: 测试相关
- `chore`: 构建过程或辅助工具的变动

#### 示例

```bash
# 新功能
feat(core): 添加多表查询支持

# 修复 Bug
fix(interceptor): 修复 PreparedStatement 参数加密问题

# 文档更新
docs(readme): 更新快速开始指南

# 重构
refactor(parser): 重构 SQL 解析逻辑

# 性能优化
perf(cache): 优化配置缓存性能
```

### Pull Request 流程

1. **Fork 项目**

```bash
# 在 GitHub 上 Fork 项目
```

2. **创建分支**

```bash
git checkout -b feature/my-feature
# 或
git checkout -b fix/my-bug-fix
```

3. **开发代码**

```bash
# 编写代码
# 添加测试
# 更新文档
```

4. **提交更改**

```bash
git add .
git commit -m "feat(core): 添加新功能"
git push origin feature/my-feature
```

5. **创建 Pull Request**

- 在 GitHub 上创建 Pull Request
- 填写详细的描述
- 关联相关 Issue

### PR 描述模板

```markdown
## 变更类型
- [ ] 新功能
- [ ] Bug 修复
- [ ] 文档更新
- [ ] 重构
- [ ] 性能优化

## 变更描述
简要描述这次变更的内容

## 变更原因
为什么需要这个变更

## 测试说明
如何验证这次变更

## 相关 Issue
关联的 Issue 编号（如果有）
```

---

## 🧪 测试要求

### 单元测试

为新功能添加单元测试：

```java
@Test
public void testEncryption() {
    // Given
    String plainText = "test";
    
    // When
    String encrypted = strategy.encryption(plainText);
    
    // Then
    assertNotNull(encrypted);
    assertNotEquals(plainText, encrypted);
}
```

### 集成测试

添加集成测试验证完整流程：

```java
@SpringBootTest
public class EncryptionIntegrationTest {
    
    @Test
    public void testInsertAndQuery() {
        // 插入数据
        User user = new User();
        user.setName("测试");
        userMapper.insert(user);
        
        // 查询验证
        User queried = userMapper.selectById(user.getId());
        assertEquals("测试", queried.getName());
    }
}
```

### 测试覆盖率

- 核心功能：覆盖率 ≥ 80%
- 关键路径：覆盖率 ≥ 90%

---

## 📚 文档要求

### 代码注释

- 所有公共类和方法必须有 JavaDoc 注释
- 复杂逻辑必须有行内注释

### 文档更新

- 新功能必须更新相关文档
- Bug 修复如果影响使用，需要更新文档
- 配置变更必须更新配置文档

### 示例代码

- 提供完整的、可运行的示例
- 示例代码应该经过测试
- 示例应该包含必要的注释

---

## 🐛 Bug 报告

### Bug 报告模板

```markdown
## 问题描述
简要描述遇到的问题

## 重现步骤
1. 第一步
2. 第二步
3. ...

## 预期行为
描述你期望的行为

## 实际行为
描述实际发生的行为

## 环境信息
- Java 版本: 1.8
- Spring Boot 版本: 2.6.13
- 数据库: MySQL 8.0
- 其他相关信息

## 日志信息
粘贴相关的日志信息

## 附加信息
其他可能有助于解决问题的信息
```

---

## 💡 功能请求

### 功能请求模板

```markdown
## 功能描述
简要描述你希望添加的功能

## 使用场景
描述这个功能的使用场景

## 预期效果
描述实现后的效果

## 可能的实现方案
如果有想法，可以描述可能的实现方案

## 相关 Issue
关联的相关 Issue
```

---

## 📋 检查清单

提交 PR 前请检查：

### 代码质量

- [ ] 代码符合项目规范
- [ ] 没有编译错误和警告
- [ ] 代码通过所有检查工具（如 Checkstyle）
- [ ] 代码已经过自测

### 测试

- [ ] 添加了单元测试
- [ ] 添加了集成测试（如需要）
- [ ] 所有测试通过
- [ ] 测试覆盖率达标

### 文档

- [ ] 更新了相关文档
- [ ] 代码注释完整
- [ ] 提供了使用示例（如需要）

### Git

- [ ] Commit 信息符合规范
- [ ] PR 描述清晰完整
- [ ] 关联了相关 Issue

---

## 🎯 开发优先级

### 高优先级

1. Bug 修复
2. 安全相关问题
3. 性能问题
4. 文档错误

### 中优先级

1. 新功能实现
2. 代码重构
3. 测试补充

### 低优先级

1. 代码风格优化
2. 文档改进
3. 示例补充

---

## 🤔 问题讨论

### 讨论渠道

- **GitHub Issues**: 用于 Bug 报告和功能请求
- **GitHub Discussions**: 用于技术讨论
- **Pull Request**: 用于代码审查和讨论

### 讨论原则

- 保持友好和尊重
- 提供有建设性的反馈
- 使用清晰的描述

---

## 📞 联系方式

如有问题，可以通过以下方式联系：

- **GitHub Issues**: [提交 Issue](https://github.com/hexlodev/securt-kit/issues)
- **GitHub Discussions**: [参与讨论](https://github.com/hexlodev/securt-kit/discussions)

---

## 🙏 致谢

感谢所有为项目做出贡献的开发者！你的贡献让项目变得更好。

---

**文档版本**: v1.0  
**最后更新**: 2025-01-XX

