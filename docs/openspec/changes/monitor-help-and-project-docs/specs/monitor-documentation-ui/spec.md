## Purpose

定义 Monitor 浏览器控制台的上下文帮助和项目文档能力，使操作人员可以在当前功能附近获得准确说明，并在不依赖外部站点的情况下完成 Securt-Kit 基础接入与配置。

## ADDED Requirements

### Requirement: Every Monitor function provides contextual help
Monitor UI SHALL 在工作台、加密解密、验签、刷数作业、SQL 和配置信息六个主功能页提供可识别且可访问的帮助入口。每个入口展示的内容 MUST 与当前功能匹配，并至少包含功能用途、操作步骤、关键字段或结果解释、限制提示和最小示例。

#### Scenario: Operator opens help from a function page
- **WHEN** 操作人员在任一 Monitor 主功能页激活帮助入口
- **THEN** 系统展示当前功能对应的帮助内容
- **AND** 不切换当前主功能页或清空已填写的数据

#### Scenario: SQL help explains each SQL mode
- **WHEN** 操作人员从 SQL 页面打开帮助
- **THEN** 帮助内容分别说明查询、SQL 解析和参数加密
- **AND** 明确字面量 SQL 没有占位符映射属于正常情况
- **AND** 明确参数加密生成结果但不执行修改 SQL

### Requirement: Contextual help uses one accessible modal
Monitor UI SHALL 使用共用模态窗口承载上下文帮助。帮助入口 MUST 包含可见含义或辅助技术标签；模态窗口 MUST 支持关闭按钮、点击遮罩和 Escape 键关闭，并 MUST 在打开与关闭时正确管理键盘焦点。

#### Scenario: Help modal opens without changing page layout
- **WHEN** 操作人员激活帮助入口
- **THEN** 共用帮助模态窗口显示在当前页面上方
- **AND** 当前功能区尺寸和滚动位置不因帮助内容展开而改变
- **AND** 焦点进入模态窗口

#### Scenario: Help modal closes by supported controls
- **WHEN** 操作人员激活关闭按钮、点击模态窗口外遮罩或按下 Escape
- **THEN** 模态窗口关闭
- **AND** 焦点返回此前激活帮助的控件

### Requirement: Project documentation is available as a main tab
Monitor UI SHALL 在顶部主导航提供独立「项目文档」Tab，且 MUST 保持工作台为首次进入主界面时的默认页面。项目文档 MUST 覆盖项目简介、Boot 2 与 Boot 3 依赖、JDBC 与 MyBatis 通道、加密字段及自定义策略、Digest、LIKE、多数据源、Monitor 与 Playground 用途以及常见问题。

#### Scenario: User opens project documentation
- **WHEN** 用户激活顶部「项目文档」入口
- **THEN** 系统在 Monitor 当前页面内展示项目文档
- **AND** 文档包含所有规定主题的可导航章节
- **AND** 不需要请求新的后端业务 API

#### Scenario: Workbench remains the default view
- **WHEN** 用户登录成功或在免登录模式下首次进入 Monitor 主界面
- **THEN** 工作台保持为默认激活 Tab
- **AND** 项目文档仅在用户主动选择后显示

### Requirement: Project documentation examples are copyable
项目文档中的配置和代码示例 SHALL 提供复制操作。复制成功或失败 MUST 向用户提供可感知反馈，且失败 MUST NOT 导致文档页面不可继续使用。

#### Scenario: Code example copy succeeds
- **WHEN** 用户激活某个文档代码示例的复制按钮且浏览器允许复制
- **THEN** 完整示例文本写入剪贴板
- **AND** 页面显示复制成功反馈

#### Scenario: Code example copy fails
- **WHEN** 浏览器拒绝剪贴板操作且降级复制同样失败
- **THEN** 页面显示复制失败反馈
- **AND** 用户仍可查看并手动选择示例文本

### Requirement: Documentation remains bounded, responsive, and offline
帮助模态窗口和项目文档正文 SHALL 使用受限高度的内部滚动区域，MUST 避免文档内容持续撑高 Monitor 页面，并 MUST 在桌面和不大于 900px 的窄屏视口中无页面级横向溢出。所有规定内容 MUST 随 Monitor 静态资源提供，不依赖外部文档站点。

#### Scenario: Long documentation on desktop
- **WHEN** 文档正文超过可见高度
- **THEN** 内容在文档区域内部滚动
- **AND** Monitor 页面不会因完整文档长度持续增长

#### Scenario: Documentation on narrow viewport
- **WHEN** 视口宽度不大于 900px
- **THEN** 文档目录调整为适合窄屏的导航方式
- **AND** 帮助及文档内容不产生页面级横向滚动

#### Scenario: Monitor is used without external network access
- **WHEN** Monitor 静态资源已加载但外部网络不可用
- **THEN** 上下文帮助和规定的项目文档内容仍可查看
