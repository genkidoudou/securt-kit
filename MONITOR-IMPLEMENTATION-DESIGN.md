# Securt-Kit 监控页面实现设计方案

> 参考 Druid 监控页面实现方式，设计 Securt-Kit 监控管理页面

---

## 📋 目录

1. [整体架构设计](#整体架构设计)
2. [文件结构设计](#文件结构设计)
3. [认证机制设计](#认证机制设计)
4. [功能模块设计](#功能模块设计)
5. [API 接口设计](#api-接口设计)
6. [前端页面设计](#前端页面设计)
7. [数据流向设计](#数据流向设计)
8. [配置设计](#配置设计)

---

## 🏗️ 整体架构设计

### 1.1 架构概览

```
┌─────────────────────────────────────────────────┐
│        Securt-Kit 监控页面架构                     │
├─────────────────────────────────────────────────┤
│                                                 │
│  浏览器请求                                     │
│    │                                            │
│    ├─→ /monitor/index.html (静态页面)            │
│    ├─→ /monitor/login (登录处理)                 │
│    └─→ /monitor/*.json (数据接口)                │
│         │                                        │
│         ▼                                        │
│  ┌──────────────────────────────────────────┐  │
│  │  MonitorController (统一处理)              │  │
│  │    - 处理登录认证                          │  │
│  │    - 处理静态资源                           │  │
│  │    - 处理 JSON 数据接口                     │  │
│  └──────────────────┬───────────────────────┘  │
│                     │                           │
│                     ▼                           │
│  ┌──────────────────────────────────────────┐  │
│  │  直接读取数据（无 Service 层）              │  │
│  │    - TableCache                           │  │
│  │    - SqlParseCache                        │  │
│  │    - StrategyCache                        │  │
│  │    - SecurtkitUtils                       │  │
│  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

### 1.2 核心设计原则

1. **统一 Controller 处理**：类似 Druid 的 StatViewServlet，一个 Controller 处理所有请求
2. **URL 后缀区分**：`.json` 结尾 = 数据接口，其他 = 静态资源或页面
3. **直接读取数据**：不需要 Service 层，直接从内存读取
4. **Session 认证**：简单实用，用户名密码从配置文件读取
5. **静态资源内嵌**：HTML/CSS/JS 放在 `resources/static/monitor/`

---

## 📁 文件结构设计

### 2.1 目录结构

```
securt-kit-core/
├── src/main/java/
│   └── io/github/hexlodev/core/
│       └── monitor/
│           ├── MonitorController.java          # 统一处理所有请求
│           ├── MonitorProperties.java          # 配置属性
│           └── MonitorAutoConfiguration.java   # 自动配置
│
└── src/main/resources/
    ├── static/
    │   └── monitor/
    │       ├── index.html                      # 主页面（登录+功能）
    │       ├── css/
    │       │   └── style.css                   # 样式文件
    │       └── js/
    │           └── app.js                      # JavaScript 逻辑
    │
    └── META-INF/
        └── spring.factories                     # 自动配置注册
```

### 2.2 文件说明

- **MonitorController.java**：
  - 统一处理 `/monitor/**` 路径的所有请求
  - 处理登录认证
  - 处理静态资源路由
  - 处理 JSON 数据接口（通过 URL 后缀判断）

- **MonitorProperties.java**：
  - 配置属性类，绑定 `securtkit.monitor.*` 配置
  - 包含：enabled、username、password、path

- **静态资源文件**：
  - `index.html`：单页应用，包含登录页面和所有功能页面
  - `style.css`：页面样式
  - `app.js`：前端逻辑（登录、API 调用、页面交互）

---

## 🔐 认证机制设计

### 3.1 认证流程

```
用户访问 /monitor/index.html
    │
    ├─→ 检查 Session 中是否有 "monitor_logged_in" 标记
    │
    ├─→ 如果没有，显示登录表单
    │
    ├─→ 用户提交用户名密码
    │
    ├─→ 后端验证（与配置文件中的用户名密码对比）
    │
    ├─→ 验证成功：设置 Session 标记，跳转到主页面
    │
    └─→ 验证失败：显示错误信息，重新登录
```

### 3.2 认证检查

**对于静态资源请求**：
- 如果未登录，重定向到登录页面

**对于 JSON 接口请求**：
- 如果未登录，返回 401 状态码

**Session 存储**：
- 登录成功：`session.setAttribute("monitor_logged_in", true)`
- 检查登录：`session.getAttribute("monitor_logged_in") != null`

### 3.3 配置方式

```yaml
securtkit:
  monitor:
    enabled: true              # 是否启用监控页面
    username: admin            # 用户名（写死在配置中）
    password: admin123         # 密码（写死在配置中）
    path: /monitor            # 访问路径前缀
```

---

## 📦 功能模块设计

### 4.1 功能清单

#### 功能 1：字符串加密
- **输入**：明文字符串、表名、字段名、加密策略（可选）
- **处理**：调用 `EncryptionHandler.handleEncryption()` 或直接调用策略
- **输出**：加密后的字符串
- **API**：`POST /monitor/encrypt.json`

#### 功能 2：字符串解密
- **输入**：加密字符串、表名、字段名、加密策略（可选）
- **处理**：调用 `EncryptionHandler.handleDecryption()` 或直接调用策略
- **输出**：解密后的字符串
- **API**：`POST /monitor/decrypt.json`

#### 功能 3：SQL 加密
- **输入**：SQL 语句（INSERT/UPDATE）、参数值（JSON 格式）
- **处理**：
  1. 使用 `SecurtkitUtils.parseSql()` 解析 SQL
  2. 识别需要加密的字段和占位符
  3. 对参数值进行加密
  4. 替换 SQL 中的占位符为加密后的值
- **输出**：转换后的 SQL 语句
- **API**：`POST /monitor/sql/encrypt.json`

#### 功能 4：查询解密
- **输入**：SELECT SQL 语句、参数值（JSON 数组格式）
- **处理**：
  1. 执行 SQL 查询（使用应用的数据源）
  2. 使用 `ResultSetDecryptingProxy.wrap()` 包装结果集
  3. 提取所有数据（自动解密）
  4. 返回 JSON 格式的结果
- **输出**：查询结果（加密字段已自动解密）
- **API**：`POST /monitor/query/decrypt.json`

### 4.2 数据流向

#### 加密功能数据流
```
前端输入 → POST /monitor/encrypt.json
    │
    ├─→ MonitorController 处理
    │
    ├─→ 从 TableCache 获取加密策略（如果提供了表名和字段名）
    │
    ├─→ 从 StrategyCache 获取策略实例
    │
    ├─→ 调用策略的 encryption() 方法
    │
    └─→ 返回加密后的字符串（JSON 格式）
```

#### SQL 加密数据流
```
前端输入 SQL 和参数 → POST /monitor/sql/encrypt.json
    │
    ├─→ MonitorController 处理
    │
    ├─→ 调用 SecurtkitUtils.parseSql() 解析 SQL
    │
    ├─→ 获取占位符到字段的映射
    │
    ├─→ 遍历参数，对需要加密的参数值进行加密
    │
    ├─→ 替换 SQL 中的占位符为加密后的值
    │
    └─→ 返回转换后的 SQL（JSON 格式）
```

#### 查询解密数据流
```
前端输入 SQL 和参数 → POST /monitor/query/decrypt.json
    │
    ├─→ MonitorController 处理
    │
    ├─→ 解析 SQL 获取表信息
    │
    ├─→ 执行 SQL（使用应用的数据源）
    │
    ├─→ 使用 ResultSetDecryptingProxy.wrap() 包装结果集
    │
    ├─→ 遍历结果集，提取所有数据（自动解密）
    │
    └─→ 返回 JSON 格式的查询结果
```

---

## 🔌 API 接口设计

### 5.1 接口列表

#### 认证相关
- `POST /monitor/login`：登录处理（表单提交）
- `GET /monitor/logout`：退出登录

#### 功能接口（JSON）
- `POST /monitor/encrypt.json`：字符串加密
- `POST /monitor/decrypt.json`：字符串解密
- `POST /monitor/sql/encrypt.json`：SQL 加密
- `POST /monitor/query/decrypt.json`：查询解密

### 5.2 接口设计

#### 5.2.1 加密接口

**请求**：`POST /monitor/encrypt.json`
```json
{
  "text": "张三",                    // 要加密的文本
  "tableName": "user",              // 表名（可选，用于获取策略）
  "fieldName": "name",              // 字段名（可选，用于获取策略）
  "strategy": "com.example.Strategy" // 策略类名（可选，优先级最高）
}
```

**响应**：
```json
{
  "success": true,
  "data": {
    "original": "张三",
    "encrypted": "encrypted_value_here"
  },
  "message": "加密成功"
}
```

#### 5.2.2 解密接口

**请求**：`POST /monitor/decrypt.json`
```json
{
  "text": "encrypted_value_here",
  "tableName": "user",
  "fieldName": "name",
  "strategy": "com.example.Strategy"  // 可选
}
```

**响应**：
```json
{
  "success": true,
  "data": {
    "encrypted": "encrypted_value_here",
    "decrypted": "张三"
  },
  "message": "解密成功"
}
```

#### 5.2.3 SQL 加密接口

**请求**：`POST /monitor/sql/encrypt.json`
```json
{
  "sql": "UPDATE user SET name = ?, phone = ? WHERE id = ?",
  "parameters": {
    "1": "张三",
    "2": "13800138000",
    "3": "1"
  }
}
```

**响应**：
```json
{
  "success": true,
  "data": {
    "originalSql": "UPDATE user SET name = ?, phone = ? WHERE id = ?",
    "convertedSql": "UPDATE user SET name = 'encrypted_value1', phone = 'encrypted_value2' WHERE id = '1'",
    "encryptedFields": [
      {
        "parameterIndex": 1,
        "fieldName": "name",
        "tableName": "user",
        "originalValue": "张三",
        "encryptedValue": "encrypted_value1"
      },
      {
        "parameterIndex": 2,
        "fieldName": "phone",
        "tableName": "user",
        "originalValue": "13800138000",
        "encryptedValue": "encrypted_value2"
      }
    ]
  },
  "message": "SQL 转换成功"
}
```

#### 5.2.4 查询解密接口

**请求**：`POST /monitor/query/decrypt.json`
```json
{
  "sql": "SELECT * FROM user WHERE id = ?",
  "parameters": ["1"]
}
```

**响应**：
```json
{
  "success": true,
  "data": {
    "columns": ["id", "name", "phone", "email"],
    "rows": [
      {
        "id": "1",
        "name": "张三",        // 已解密
        "phone": "13800138000", // 已解密
        "email": "zhangsan@example.com"
      }
    ],
    "rowCount": 1
  },
  "message": "查询成功"
}
```

### 5.3 错误处理

统一错误响应格式：
```json
{
  "success": false,
  "message": "错误描述",
  "data": null
}
```

错误场景：
- 401：未登录
- 400：参数错误
- 500：服务器内部错误

---

## 🎨 前端页面设计

### 6.1 页面结构

**单页应用**：所有功能在一个 HTML 文件中，通过 JavaScript 控制显示/隐藏

```
index.html
├── 登录页面（初始显示）
│   ├── 用户名输入框
│   ├── 密码输入框
│   └── 登录按钮
│
└── 主功能页面（登录后显示）
    ├── 头部（标题 + 退出按钮）
    ├── 导航栏（Tab 切换）
    │   ├── Tab 1: 字符串加密
    │   ├── Tab 2: 字符串解密
    │   ├── Tab 3: SQL 加密
    │   └── Tab 4: 查询解密
    │
    └── 内容区域（根据 Tab 显示不同内容）
```

### 6.2 功能页面设计

#### Tab 1: 字符串加密

```
┌─────────────────────────────────────┐
│ 字符串加密                            │
├─────────────────────────────────────┤
│ 表名: [输入框]                        │
│ 字段名: [输入框]                      │
│ 加密策略: [下拉框]                    │
│ 明文: [多行文本输入框]                 │
│ [加密] [清空]                         │
├─────────────────────────────────────┤
│ 加密结果: [只读多行文本输入框]          │
│ [复制结果]                            │
└─────────────────────────────────────┘
```

#### Tab 2: 字符串解密

```
┌─────────────────────────────────────┐
│ 字符串解密                            │
├─────────────────────────────────────┤
│ 表名: [输入框]                        │
│ 字段名: [输入框]                      │
│ 加密策略: [下拉框]                    │
│ 密文: [多行文本输入框]                 │
│ [解密] [清空]                         │
├─────────────────────────────────────┤
│ 解密结果: [只读多行文本输入框]          │
│ [复制结果]                            │
└─────────────────────────────────────┘
```

#### Tab 3: SQL 加密

```
┌─────────────────────────────────────┐
│ SQL 加密                             │
├─────────────────────────────────────┤
│ 原始 SQL: [多行文本输入框]             │
│ UPDATE user SET name = ?, phone = ? │
│ WHERE id = ?                         │
│                                      │
│ 参数值（JSON 格式）: [多行文本输入框]   │
│ {                                    │
│   "1": "张三",                       │
│   "2": "13800138000",                │
│   "3": "1"                           │
│ }                                    │
│ [转换] [清空]                         │
├─────────────────────────────────────┤
│ 转换结果: [只读多行文本输入框]          │
│ UPDATE user SET                     │
│   name = 'encrypted_value1',        │
│   phone = 'encrypted_value2'        │
│ WHERE id = '1'                       │
│ [复制结果]                            │
└─────────────────────────────────────┘
```

#### Tab 4: 查询解密

```
┌─────────────────────────────────────┐
│ 查询解密                             │
├─────────────────────────────────────┤
│ 查询 SQL: [多行文本输入框]             │
│ SELECT * FROM user WHERE id = ?     │
│                                      │
│ 参数值（JSON 数组格式）: [输入框]      │
│ ["1"]                                │
│ [执行查询] [清空]                      │
├─────────────────────────────────────┤
│ 查询结果: [表格展示]                   │
│ ┌────┬──────┬──────────────┬──────┐ │
│ │ ID │ Name │ Phone        │ Email│ │
│ ├────┼──────┼──────────────┼──────┤ │
│ │ 1  │ 张三 │ 13800138000  │ ...  │ │
│ └────┴──────┴──────────────┴──────┘ │
│ [导出 CSV]                            │
└─────────────────────────────────────┘
```

### 6.3 前端交互流程

#### 登录流程
```
1. 页面加载时检查 Session
   └─→ 调用 GET /monitor/api/check.json
   
2. 如果未登录，显示登录表单
   
3. 用户提交登录
   └─→ POST /monitor/login (表单提交)
   └─→ 成功后跳转到主页面
   
4. 如果已登录，显示主功能页面
```

#### 加密流程
```
1. 用户输入明文和相关参数
2. 点击"加密"按钮
3. 调用 POST /monitor/encrypt.json
4. 显示加密结果
```

#### SQL 加密流程
```
1. 用户输入 SQL 和参数（JSON 格式）
2. 点击"转换"按钮
3. 调用 POST /monitor/sql/encrypt.json
4. 显示转换后的 SQL
5. 可选：显示加密字段的详细信息
```

#### 查询解密流程
```
1. 用户输入 SQL 和参数（JSON 数组格式）
2. 点击"执行查询"按钮
3. 调用 POST /monitor/query/decrypt.json
4. 显示查询结果（表格形式）
5. 加密字段已自动解密
```

---

## 🔄 数据流向设计

### 7.1 Controller 处理流程

```
MonitorController.handleRequest()
    │
    ├─→ 提取请求 URI
    │
    ├─→ 判断是否为登录相关请求
    │   └─→ 是：处理登录逻辑
    │
    ├─→ 检查认证（Session）
    │   ├─→ 未登录且是静态资源 → 重定向到登录页面
    │   └─→ 未登录且是 JSON 接口 → 返回 401
    │
    ├─→ 判断请求类型（通过 URL 后缀）
    │   ├─→ .json 结尾 → 处理 JSON 数据接口
    │   └─→ 其他 → 返回静态资源（Spring Boot 自动处理）
    │
    └─→ JSON 接口处理：
        ├─→ encrypt.json → 调用加密逻辑
        ├─→ decrypt.json → 调用解密逻辑
        ├─→ sql/encrypt.json → 调用 SQL 加密逻辑
        └─→ query/decrypt.json → 调用查询解密逻辑
```

### 7.2 数据处理细节

#### 加密逻辑
```
1. 从请求 JSON 中提取参数
2. 确定加密策略：
   - 如果提供了 strategy 参数 → 使用指定的策略类
   - 否则，如果提供了 tableName 和 fieldName → 从 TableCache 获取策略
   - 否则，使用默认策略
3. 从 StrategyCache 获取策略实例
4. 调用策略的 encryption() 方法
5. 返回加密结果
```

#### SQL 加密逻辑
```
1. 从请求 JSON 中提取 SQL 和参数
2. 调用 SecurtkitUtils.parseSql() 解析 SQL
3. 获取占位符到字段的映射（pair.getKey()）
4. 遍历参数：
   - 根据参数索引找到对应的字段信息
   - 从 TableCache 获取该字段的加密策略
   - 对参数值进行加密
   - 替换 SQL 中的占位符（注意转义单引号）
5. 返回转换后的 SQL 和加密字段信息
```

#### 查询解密逻辑
```
1. 从请求 JSON 中提取 SQL 和参数
2. 调用 SecurtkitUtils.parseSql() 解析 SQL（获取表信息和字段映射）
3. 从应用的数据源获取 Connection
4. 创建 PreparedStatement，设置参数
5. 执行查询，获取 ResultSet
6. 使用 ResultSetDecryptingProxy.wrap() 包装结果集
   - 传入：ResultSet、表名集合、字段映射、SQL
7. 遍历包装后的 ResultSet，提取所有数据（自动解密）
8. 返回 JSON 格式的结果
```

---

## ⚙️ 配置设计

### 8.1 配置属性类

```java
@ConfigurationProperties(prefix = "securtkit.monitor")
public class MonitorProperties {
    /**
     * 是否启用监控页面
     */
    private boolean enabled = true;
    
    /**
     * 监控页面用户名
     */
    private String username = "admin";
    
    /**
     * 监控页面密码
     */
    private String password = "admin123";
    
    /**
     * 监控页面访问路径前缀
     */
    private String path = "/monitor";
}
```

### 8.2 自动配置

```java
@Configuration
@ConditionalOnProperty(prefix = "securtkit.monitor", 
                      name = "enabled", 
                      havingValue = "true",
                      matchIfMissing = false)
@EnableConfigurationProperties(MonitorProperties.class)
public class MonitorAutoConfiguration {
    
    @Bean
    public MonitorController monitorController(MonitorProperties properties) {
        return new MonitorController(properties);
    }
    
    @Bean
    public MonitorAuthInterceptor monitorAuthInterceptor() {
        return new MonitorAuthInterceptor();
    }
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(monitorAuthInterceptor())
            .addPathPatterns("/monitor/**")
            .excludePathPatterns("/monitor/login", "/monitor/logout");
    }
}
```

### 8.3 使用配置

```yaml
securtkit:
  monitor:
    enabled: true
    username: admin
    password: admin123
    path: /monitor
```

---

## 📋 实现要点总结

### 9.1 核心设计原则

1. ✅ **统一 Controller**：一个 Controller 处理所有请求（登录、静态资源、JSON 接口）
2. ✅ **URL 后缀区分**：`.json` = 数据接口，其他 = 静态资源
3. ✅ **直接读取数据**：不需要 Service 层，直接从 TableCache、SqlParseCache 等读取
4. ✅ **Session 认证**：简单实用，用户名密码从配置读取
5. ✅ **静态资源内嵌**：HTML/CSS/JS 放在 `resources/static/monitor/`

### 9.2 关键技术点

1. **Controller 实现**：
   - 使用 `@GetMapping("/**")` 或 `@RequestMapping("/monitor/**")` 捕获所有请求
   - 通过 `request.getRequestURI()` 判断请求类型
   - JSON 接口直接读取数据并返回，不需要 Service 层

2. **认证处理**：
   - 登录：表单提交到 `/monitor/login`，验证后设置 Session
   - 检查：拦截器检查 Session，未登录返回 401 或重定向

3. **数据获取**：
   - 加密/解密：从 TableCache 获取策略，从 StrategyCache 获取实例
   - SQL 加密：调用 SecurtkitUtils.parseSql() 解析，直接处理
   - 查询解密：使用 ResultSetDecryptingProxy.wrap() 包装结果集

4. **前端实现**：
   - 单页应用，JavaScript 控制显示/隐藏
   - 使用 Fetch API 调用 JSON 接口
   - 登录后保存状态，页面刷新时检查登录状态

### 9.3 注意事项

1. **数据源获取**：
   - 查询解密功能需要访问应用的数据源
   - 可以通过 `@Autowired DataSource` 注入
   - 或者在 Controller 中通过 Spring 上下文获取

2. **安全性**：
   - 生产环境必须修改默认密码
   - 建议限制访问 IP（通过 Nginx 或拦截器）
   - 查询解密功能需要谨慎，避免 SQL 注入

3. **错误处理**：
   - 统一错误响应格式
   - 前端显示友好的错误信息
   - 记录错误日志便于排查

---

**设计方案完成时间**：2025-01-XX  
**参考实现**：Druid StatViewServlet  
**版本**：v1.0

