# 测试工程 Playground 交互演示页设计方案

> 状态：**已实现**  
> 版本：v1.0  
> 日期：2026-09-06  
> 目的：在四个测试工程中提供统一的浏览器演示页，便于手工验证加解密 / 摘要 / 复杂查询 / 多数据源；**保留 JUnit 作为 CI 回归**。

---

## 1. 背景与目标

### 1.1 现状

| 能力 | 现状 |
|------|------|
| JUnit | `securt-kit-test-boot2/3`、`securt-kit-dy-datasource-test-boot2/3` 内大量集成测 |
| `/monitor` | `securt-kit-monitor` 运维试算（加解密、解析 SQL、刷数） |
| 多 DS REST | `dy-datasource-test-*` 有 JSON API，无专用操作页 |

### 1.2 目标

1. **新建共享模块** `securt-kit-playground`（方案 C），四个测试工程共用一套页面  
2. 覆盖能力：**CRUD、摘要 Digest、SQL 试算入口、复杂查询、多数据源**  
3. **JUnit 保留**（方案 A）：页面是演示与手工验收，不是替代 CI  
4. 挂载方式对齐 monitor：共享 Engine + Dispatcher，boot2/boot3 薄 Servlet

### 1.3 非目标

- 删除或大幅削弱 JUnit  
- 把 playground 并入 `/monitor` 单一巨型页  
- 开放任意自由 SQL（复杂查询用固定 action；自由 SQL 走 monitor）  
- 四工程各自复制一整套前端  

---

## 2. 决策摘要

| 议题 | 决策 |
|------|------|
| 与 JUnit 关系 | 保留 JUnit；另加交互演示页 |
| 能力范围 | 1–5 全做：CRUD / Digest / SQL / Complex / Multi-DS |
| 模块形态 | 新模块 `securt-kit-playground`（**仅测试工程依赖**），由各 test 应用自行挂载 Servlet；**不**打进 starter |
| SQL 试算 | 第一期深链 `/monitor`，不复制 parse/encrypt-sql |
| UI 技术 | 静态 HTML/CSS/JS，风格对齐 monitor |
| 安全 | 表白名单；可选鉴权（本地可 `auth-enabled: false`） |

---

## 3. 模块与挂载

```text
securt-kit-playground          # 测试专用共享内核（无 Servlet）
  ├── PlaygroundProperties / PlaygroundEngine / PlaygroundDispatcher
  ├── dto + 静态资源 support/playground/http/resources/
  └── （无 Servlet API）

各 test 工程自行：
  依赖 securt-kit-playground
  + PlaygroundStatViewServlet / PlaygroundWebConfiguration（javax 或 jakarta）

starter-boot2/3 不含 playground（仅含 core / monitor / mybatis 等产品能力）

测试工程：securtkit.playground.enabled=true → /playground/
```

与 `/monitor` **并存**：

- monitor = 运维试算  
- playground = 业务表演示（CRUD / 摘要 / 复杂查询 / 多 DS）

---

## 4. 页面信息架构

**入口：** `http://localhost:<port>/playground/`

**顶栏：** 标题、数据源下拉（多 DS 显示；单 DS 隐藏/固定 default）、链接到 `/monitor`

| Tab | 行为 |
|-----|------|
| CRUD | 白名单选表 → insert / update / query；展示解密结果；可选 raw 看库内密文/摘要 |
| Digest | insert 不带摘要列 → 看落库 digest；改 phone；篡改摘要 + 验签读取 |
| SQL | 深链 / 跳转 monitor SQL 能力 |
| Complex | 按钮触发 join / like / page / batch 等固定 action |
| Multi-DS | 与顶栏 DS 联动；或对照两源执行同一演示 |

原则：一屏一主操作 + 结果区；篡改类操作二次确认。

---

## 5. API 与 Engine 边界

### 5.1 分层

| 层 | 职责 |
|----|------|
| Servlet 适配 | javax / jakarta |
| Dispatcher | 静态资源 + `/api/*`、可选登录 |
| Engine | `resolve(datasourceId)` → DataSource；执行演示操作 |
| 静态资源 | index.html / css / js |

### 5.2 API（第一期）

```text
GET  /playground/api/meta.json
GET  /playground/api/datasources.json
POST /playground/api/crud/insert.json
POST /playground/api/crud/update.json
POST /playground/api/crud/query.json
POST /playground/api/crud/raw-query.json      # 可选，仅白名单
POST /playground/api/digest/demo.json         # insert | update-phone | tamper | verify-read
POST /playground/api/complex/run.json         # action=...
```

### 5.3 约束

- **表白名单**（`allowed-tables`）  
- Complex：**固定 action**，不开放任意 SQL  
- 单 DS 工程对不适用的 complex/multi-ds action → 明确错误「当前工程不支持」  
- 多 DS：注入 `Map<String, DataSource>` 或 `PlaygroundDataSourceLocator` Bean  

---

## 6. 配置与四工程启用

```yaml
securtkit:
  playground:
    enabled: true
    path: /playground
    auth-enabled: false
    allowed-tables: [user, digest_user, orders]
```

四个测试工程 README 增加：启动后访问 `/playground/`。

---

## 7. 实现分期

| 阶段 | 内容 |
|------|------|
| P0 | 模块骨架 + Servlet 挂载 + CRUD（单 DS boot2） |
| P1 | Digest Tab + raw-query；boot3 挂载 |
| P2 | 多 DS 下拉 + Complex Tab；dy-boot2/3 启用 |
| P3 | SQL 深链 monitor；文档与四工程 README |

---

## 8. 自检记录

- [x] 与头脑风暴决策一致（A + 能力 1–5 + 模块 C + 方案 1）  
- [x] JUnit 保留写明  
- [x] 与 monitor 职责边界清晰  
- [x] 白名单 / 固定 action 安全边界写明  
- [x] 未包含实现代码；待用户审阅后再 writing-plans  

---

## 9. 审阅清单

1. 模块挂载与 starter 集成方式  
2. Tab 与能力 1–5 映射  
3. API 与表白名单  
4. 分期 P0–P3  
