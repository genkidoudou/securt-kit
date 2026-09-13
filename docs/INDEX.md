# 文档索引

## 用户手册（推荐）

面向接入与运维的手册在仓库根目录 **[`doc/`](../doc/)**，并通过 GitHub Actions 同步到 [Wiki](https://github.com/genkidoudou/securt-kit/wiki)。

- [Home](../doc/Home.md) · [Quick-Start](../doc/Quick-Start.md) · [Usage](../doc/Usage.md)
- [Monitor](../doc/Monitor.md) · [Playground](../doc/Playground.md) · [Multi-Datasource](../doc/Multi-Datasource.md) · [FAQ](../doc/FAQ.md)
- [Wiki 同步说明](../doc/README.md)

以下 `docs/` 条目偏设计说明与历史详版，可与 `doc/` 对照阅读。

## 核心文档

- [README.md](../README.md) - 项目主文档
- [快速开始](QUICK-START.md) - 5 分钟快速集成指南
- [使用指南](USAGE.md) - 详细使用说明
- [多数据源配置](MULTI-DATASOURCE.md) - 多数据源场景配置指南
- [Playground 加密生命周期](PLAYGROUND.md) - 六步加密、解密、摘要与篡改验证手册
- [Maven Central 发布](MAVEN-CENTRAL-PUBLISHING.md) - Portal + central-publishing-maven-plugin（`io.github.genkidoudou`）

## 设计方案

- [MyBatis 模式与 JDBC 融合设计](MYBATIS-MODE-DESIGN.md) - ①/③ 模式切换、配置共用、FieldCryptoService 融合与 P0～P2 分期
- [监控 UI Servlet 改造](MONITOR-SERVLET.md) - Druid 风格共享 monitor 模块 + boot2/boot3 薄适配层
- [LIKE 模式处理](LIKE-HANDLER.md) - LikePatternHandler 接口与默认精确匹配实现
- [能力补强说明](CAPABILITY-GAPS.md) - 比较符/BETWEEN/NOT/函数/ON DUPLICATE 等已补项
- [字段完整性摘要设计](superpowers/specs/2026-09-05-field-digest-integrity-design.md) - Digest 配置 / 策略扩展 / 双通道写读验签（已实现）
- [Playground 交互演示页设计](superpowers/specs/2026-09-06-playground-ui-design.md) - 四测试工程专用演示 UI（不进入 starter；`/playground/`）
- [Playground 生命周期重设计](superpowers/specs/2026-09-08-playground-ui-redesign-design.md) - 单记录六步流程与三视角证据
- [Monitor 运维台增强设计](superpowers/specs/2026-09-06-monitor-ops-console-design.md) - 配置全览 / 刷数预览回写 / 验签 / SELECT 双视图（已实现）
- [Monitor UI 重设计](superpowers/specs/2026-09-07-monitor-ui-redesign-design.md) - 工作台默认页 / SQL 合并 / 刷数步骤条（方案 3）

## 模块文档

- [Core 模块](../securt-kit-core/README.md) - 核心模块文档
- [Core 使用文档](../securt-kit-core/USAGE.md) - Core 模块使用说明

## 测试项目文档

### Spring Boot 2.7

- [测试项目](../securt-kit-test-boot2/README.md) - Boot 2.7 测试项目说明
- [多数据源测试](../securt-kit-dy-datasource-test-boot2/README.md) - Boot 2.7 多数据源测试说明

### Spring Boot 3.x

- [测试项目](../securt-kit-test-boot3/README.md) - Boot 3.x 测试项目说明
- [多数据源测试](../securt-kit-dy-datasource-test-boot3/README.md) - Boot 3.x 多数据源测试说明

## 其他文档

- [贡献指南](../CONTRIBUTING.md) - 如何贡献代码

## 文档结构

```
doc/                     # 用户手册（同步 GitHub Wiki）
├── Home.md
├── Quick-Start.md
├── Usage.md
├── Monitor.md
├── Playground.md
├── Multi-Datasource.md
├── FAQ.md
├── _Sidebar.md
└── README.md            # Wiki 同步操作说明

docs/
├── INDEX.md             # 文档索引（本文件）
├── QUICK-START.md       # 快速开始
├── USAGE.md             # 使用指南
├── MULTI-DATASOURCE.md  # 多数据源配置
├── PLAYGROUND.md        # Playground 手册
├── MYBATIS-MODE-DESIGN.md
├── MONITOR-SERVLET.md
├── LIKE-HANDLER.md
└── CAPABILITY-GAPS.md
```

