# 构建指南

## 构建方式说明

项目使用 **Maven Modules + Profile** 的方式控制不同 Spring Boot 版本的模块构建：

- **父 POM 默认只包含 Boot 2.7 模块**：确保在 Java 8 环境下不会尝试编译 Boot 3 模块
- **Boot 3 模块使用 `-pl` 参数构建**：需要 Java 17+ 环境时，使用 `-pl` 参数明确指定要构建的模块
- **这是 Maven 最可靠的模块排除方式**：直接控制 `<modules>` 列表，而不是依赖 skip 属性

## 构建 Spring Boot 2.7 版本（默认，Java 8+）

```bash
mvn clean install -Pboot2
```

或者直接运行（boot2 是默认 profile）：

```bash
mvn clean install
```

**说明**：
- 使用 `boot2` profile 时，会自动跳过所有 Boot 3 模块
- 只构建：`securt-kit-core`、`securt-kit-starter-boot2`、`securt-kit-test-boot2`、`securt-kit-dy-datasource-test-boot2`
- 需要 Java 8+ 环境

## 构建 Spring Boot 3.x 版本（需要 Java 17+）

```bash
mvn clean install -Pboot3 -pl securt-kit-core,securt-kit-starter-boot3,securt-kit-test-boot3,securt-kit-dy-datasource-test-boot3
```

**说明**：
- 使用 `boot3` profile 设置 Spring Boot 版本
- 使用 `-pl` 参数指定要构建的 Boot 3 模块（**不包含 boot2 模块**）
- **不使用 `-am` 参数**，因为 `securt-kit-core` 已经在 `-pl` 列表中了
- 需要 Java 17+ 环境

**参数说明**：
- `-Pboot3`: 激活 boot3 profile，设置 Spring Boot 版本为 3.x
- `-pl`: 指定要构建的模块列表（projects list），只构建这些模块，不会构建 boot2 模块

**注意**：`-pl` 参数会**只构建指定的模块**，不会构建父 POM `<modules>` 列表中的其他模块（如 boot2 模块）。

## 构建所有模块（需要 Java 17+）

```bash
mvn clean install -Pall -pl securt-kit-core,securt-kit-starter-boot2,securt-kit-starter-boot3,securt-kit-test-boot2,securt-kit-test-boot3,securt-kit-dy-datasource-test-boot2,securt-kit-dy-datasource-test-boot3 -am
```

**说明**：
- 使用 `all` profile 设置 Spring Boot 版本
- 使用 `-pl` 参数指定要构建的所有模块
- 使用 `-am` 参数同时构建指定模块的依赖模块
- 需要 Java 17+ 环境（因为 Boot 3 模块需要 Java 17）

## Profile 说明

| Profile | 说明 | 构建的模块 | Java 版本要求 |
|---------|------|-----------|--------------|
| `boot2` (默认) | 仅构建 Boot 2.7 模块 | core + starter-boot2 + test-boot2 + dy-datasource-test-boot2 | Java 8+ |
| `boot3` | 仅构建 Boot 3.x 模块 | core + starter-boot3 + test-boot3 + dy-datasource-test-boot3 | Java 17+ |
| `all` | 构建所有模块 | 所有模块 | Java 17+ |

## 测试跳过配置

**默认情况下，所有 profile 都会跳过测试**（`maven.test.skip=true`）。

如果需要在构建时运行测试，可以：

1. **临时覆盖**：使用命令行参数
   ```bash
   mvn clean install -Pboot2 -Dmaven.test.skip=false
   ```

2. **修改 profile**：在父 POM 的 profile 中将 `maven.test.skip` 设置为 `false`

## 工作原理

1. **父 POM** 的默认 `<modules>` 只包含 Boot 2.7 模块，确保在 Java 8 环境下不会尝试编译 Boot 3 模块
2. **Profile** 通过设置 Spring Boot 版本来控制依赖版本
3. **Profile** 通过设置 `maven.test.skip=true` 来统一跳过所有测试
4. **Boot 3 模块** 使用 `-pl` 参数明确指定要构建的模块，这是 Maven 最可靠的模块排除方式

## 为什么使用这种方式？

Maven 的 `skip` 属性在某些情况下不够可靠，特别是在编译阶段。直接控制 `<modules>` 列表是最可靠的方式：

- ✅ **最可靠**：Maven 不会尝试构建不在 `<modules>` 列表中的模块
- ✅ **避免编译错误**：在 Java 8 环境下不会尝试编译需要 Java 17 的模块
- ✅ **清晰明确**：通过 `-pl` 参数明确指定要构建的模块

## 注意事项

⚠️ **IDEA 可能无法识别未在 `<modules>` 中的模块**：
- 如果需要在 IDEA 中查看 Boot 3 模块，可以临时取消注释父 POM 中的 Boot 3 模块
- 或者，在 IDEA 中手动添加 Boot 3 模块到项目结构中
- 或者，使用 `-Pall` profile 时，临时取消注释所有模块

## 优势

✅ **最可靠**：直接控制 `<modules>` 列表，Maven 不会尝试构建未列出的模块  
✅ **避免编译错误**：在 Java 8 环境下不会尝试编译需要 Java 17 的模块  
✅ **清晰明确**：通过 `-pl` 参数明确指定要构建的模块  
✅ **灵活**：可以轻松切换构建的模块集合

