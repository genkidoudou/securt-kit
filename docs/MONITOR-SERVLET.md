# 监控 UI：Druid 风格 Servlet 改造说明

> 状态：已实现  
> 更新日期：2026-09-04

## 背景

原先 `securt-kit-starter-boot2` / `securt-kit-starter-boot3` 各自复制了一整套 Spring MVC 监控实现（`MonitorController` + 静态资源），仅因 `javax` / `jakarta` 与 Boot 自动配置机制不同而分叉，维护成本高。

参考 **Alibaba Druid** 的 `StatViewServlet` 做法，改为：

- **共享模块**承载页面与业务
- **Boot2 / Boot3** 仅保留薄 Servlet 适配层

## 目标结构

```text
securt-kit-monitor          # 共享：静态资源 + MonitorEngine + Dispatcher（无 Servlet API）
        ▲
        │
┌───────┴────────┐
│ starter-boot2  │  MonitorStatViewServlet (javax.servlet)  + ServletRegistrationBean
│ starter-boot3  │  MonitorStatViewServlet (jakarta.servlet) + ServletRegistrationBean
└────────────────┘
```

## 模块职责

| 模块 | 职责 |
|------|------|
| `securt-kit-monitor` | `MonitorEngine`（加解密/SQL/刷数等业务）、`MonitorDispatcher`（路由）、`MonitorExchange`、`MonitorResourceLoader`、DTO、静态资源 `support/http/resources/` |
| `securt-kit-starter-boot2` | `MonitorStatViewServlet`（javax）+ `MonitorAutoConfiguration` |
| `securt-kit-starter-boot3` | `MonitorStatViewServlet`（jakarta）+ `MonitorAutoConfiguration` |

## 请求流程

```text
浏览器 /monitor/*
  → ServletRegistrationBean 映射的 MonitorStatViewServlet
  → 填充 MonitorExchange（method / pathInfo / params / body / session）
  → MonitorDispatcher.dispatch
       ├─ 静态资源：classpath support/http/resources/**
       └─ API：MonitorEngine.*
  → Servlet 写回 Session / Redirect / JSON / 字节流
```

## 使用方式（与改造前兼容）

```yaml
securtkit:
  monitor:
    enabled: true
    username: admin
    password: admin123
    path: /monitor
    # 主键优先于 MyBatis-Plus TableInfo
    table-primary-keys:
      user: id
      digest_user: id
    # batch-allow-empty-where: false
    # batch-allow-unconfigured-tables: false
```

访问：`http://localhost:8080/monitor/`（或 `/monitor/index.html`）

### 页面信息架构（UI 重设计）

| Tab | 说明 |
|-----|------|
| **工作台**（默认） | 组合 `config` + `datasources` 展示摘要；快捷入口跳转 SQL / 刷数 / 配置 |
| 加密解密 | 单值 encrypt / decrypt / sign / verify |
| 单行验签 | 表 + id |
| 刷数作业 | 样本预览 → 异步分批作业（进度 / 暂停 / 继续 / 取消） |
| **SQL** | 子模式：查询双视图（默认）/ 解析 / 参数加密 |
| 数据初始化 | 演示辅助 |
| 配置信息 | 加密字段 / digest / 全局开关全览 |

视觉参考与设计：`docs/superpowers/specs/2026-09-07-monitor-ui-redesign-design.md`、`docs/superpowers/mockups/monitor-redesign/`。

### 运维台能力（增强）

| 能力 | 说明 |
|------|------|
| 配置全览 | 加密字段/策略 + digest 规则 + 全局 mode/verify；HMAC key 仅布尔脱敏 |
| 加解密 / 签名验签 | 单值工具；单行验签（表+id） |
| 刷数作业 | 样本预览 + 异步 keyset 分批；`memory`/`database` 作业仓；硬顶默认 20000 |
| SQL 查询 | **仅 SELECT**；同次返回明文与密文（需 `skip-comment.enable=true`） |
| 其它 | SQL 解析、参数加密预览、数据初始化 |

### 异步刷数配置

```yaml
securtkit:
  monitor:
    batch:
      store: memory          # memory | database（目标库系统表）
      max-rows: 20000
      chunk-size: 200
      sample-size: 20
      max-failure-records: 1000
      memory-ttl-ms: 3600000
```

API 概要：

- `POST /api/batch/preview.json` — 样本 + `totalEstimated`（不创建可 apply 快照）
- `POST /api/batch/jobs.json` — 创建并调度异步作业
- `GET /api/batch/jobs/{id}.json` — 状态/进度
- `POST /api/batch/jobs/{id}/pause|resume|cancel.json`
- `POST /api/batch/apply.json` — **已废弃**（返回引导错误）

产品设计：`docs/superpowers/specs/2026-09-10-batch-job-async-design.md`。

密文双视图前提：

```yaml
securtkit:
  encryptor:
    skip-comment:
      enable: true
      token: SECURT_SKIP
```

## 为何仍保留两个 Starter

| 部分 | 是否共用 |
|------|----------|
| HTML / JS / CSS / 业务引擎 | ✅ 仅在 `securt-kit-monitor` |
| Servlet API | ❌ Boot2=`javax.servlet`，Boot3=`jakarta.servlet` |
| 自动配置注册 | ❌ `spring.factories` vs `AutoConfiguration.imports` |
| Spring Boot BOM / Java 版本 | ❌ 2.7+Java8 vs 3.x+Java17 |

UI 功能与静态资源不再双份维护；两个 Starter 只保留约一个 Servlet + 自动配置类。

## 相关代码

- `securt-kit-monitor/src/main/java/io/github/hexlodev/monitor/MonitorEngine.java`
- `securt-kit-monitor/src/main/java/io/github/hexlodev/monitor/support/MonitorDispatcher.java`
- `securt-kit-starter-boot2/.../MonitorStatViewServlet.java`
- `securt-kit-starter-boot3/.../MonitorStatViewServlet.java`
