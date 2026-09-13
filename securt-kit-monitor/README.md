# securt-kit-monitor

Securt-Kit 监控 UI 共享模块（Druid `StatViewServlet` 风格）。

## 职责

- 静态页面：`support/http/resources/`（index.html / css / js）
- 业务引擎：`MonitorEngine`
- 路由分发：`MonitorDispatcher` + `MonitorExchange`
- **不依赖** `javax.servlet` / `jakarta.servlet` / Spring MVC

由 `securt-kit-starter-boot2` / `securt-kit-starter-boot3` 的薄 Servlet 适配层接入。

详见 [docs/MONITOR-SERVLET.md](../docs/MONITOR-SERVLET.md)。
