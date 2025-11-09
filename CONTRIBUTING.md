# 贡献指南

感谢你对 Securt-Kit 项目的关注！我们欢迎所有形式的贡献。

## 如何贡献

### 报告问题

如果你发现了 bug 或有功能建议，请：

1. 检查 [Issues](https://github.com/hexlodev/securt-kit/issues) 中是否已有相关问题
2. 如果没有，创建新的 Issue，详细描述问题或建议

### 提交代码

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

### 代码规范

- 遵循 Java 编码规范
- 添加必要的注释
- 编写单元测试
- 确保所有测试通过

## 开发环境

### 构建项目

```bash
# 构建 Boot 2.7 版本
mvn clean install -Pboot2

# 构建 Boot 3.x 版本（需要 Java 17）
mvn clean install -Pboot3
```

### 运行测试

```bash
mvn test
```

## 许可证

贡献的代码将采用 Apache License 2.0 许可证。

