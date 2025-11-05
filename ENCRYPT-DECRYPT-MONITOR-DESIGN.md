# Securt-Kit 加密解密监控页面设计方案

> 参考 Druid 监控页面实现方式，设计一个简单、低耦合、低依赖的加密解密工具页面

---

## 📋 目录

1. [设计目标](#设计目标)
2. [整体架构设计](#整体架构设计)
3. [文件结构设计](#文件结构设计)
4. [功能模块设计](#功能模块设计)
5. [API 接口设计](#api-接口设计)
6. [安全设计](#安全设计)
7. [实现细节](#实现细节)

---

## 🎯 设计目标

### 核心需求
1. **输入内容**：用户可以输入需要加密或解密的文本
2. **加密功能**：点击加密按钮，对输入内容进行加密
3. **解密功能**：点击解密按钮，对输入内容进行解密

### 设计原则
- ✅ **低耦合**：监控页面模块独立，不依赖业务代码
- ✅ **低依赖**：仅依赖核心加密解密接口和 Spring MVC（项目已依赖 Spring Boot）
- ✅ **保证安全**：提供认证机制，防止未授权访问
- ✅ **开发效率**：使用 Spring MVC Controller，充分利用 Spring 特性，代码简洁高效

---

## 🏗️ 整体架构设计

### 2.1 架构概览

```
┌─────────────────────────────────────────────────┐
│        Securt-Kit 监控页面架构                     │
├─────────────────────────────────────────────────┤
│                                                 │
│  浏览器请求                                     │
│    │                                            │
│    ├─→ /monitor/index.html (静态页面)            │
│    ├─→ /monitor/login (登录处理)                 │
│    └─→ /monitor/api/*.json (数据接口)            │
│         │                                        │
│         ▼                                        │
│  ┌──────────────────────────────────────────┐  │
│  │  MonitorController (统一处理)              │  │
│  │    - 处理登录认证                          │  │
│  │    - 处理静态资源                           │  │
│  │    - 处理加密解密接口                       │  │
│  └──────────────────┬───────────────────────┘  │
│                     │                           │
│                     ▼                           │
│  ┌──────────────────────────────────────────┐  │
│  │  核心依赖（最小化）                         │  │
│  │    - TableCache（获取表字段策略）           │  │
│  │    - StrategyCache（获取策略实例）          │  │
│  │    - FieldEncryptorStrategy（执行加密解密）│  │
│  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

### 2.2 核心设计原则

1. **统一 Controller 处理**：使用 Spring MVC `@RestController`，一个 Controller 处理所有请求
2. **Spring MVC 特性**：充分利用 `@RequestMapping`、`@RequestBody`、`@Autowired` 等 Spring 特性
3. **直接调用核心组件**：不需要 Service 层，直接调用 TableCache、StrategyCache
4. **Session 认证**：简单实用，用户名密码从配置文件读取
5. **静态资源内嵌**：HTML/CSS/JS 放在 `resources/static/monitor/`，Spring Boot 自动处理
6. **技术选型说明**：使用 Controller 而非 Servlet，因为项目已依赖 Spring Boot，使用 Controller 更简洁高效（详见 `SERVLET-VS-CONTROLLER-COMPARISON.md`）

---

## 📁 文件结构设计

### 3.1 目录结构

```
securt-kit-ui/
├── src/main/java/
│   └── io/github/hexlodev/ui/
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
        └── spring.factories                     # 自动配置注册（如果需要）
```

### 3.2 文件说明

- **MonitorController.java**：
  - 统一处理 `/monitor/**` 路径的所有请求
  - 处理登录认证
  - 处理静态资源路由
  - 处理加密解密 JSON 接口（通过 URL 后缀判断）

- **MonitorProperties.java**：
  - 配置属性类，绑定 `securtkit.monitor.*` 配置
  - 包含：enabled、username、password、path

- **静态资源文件**：
  - `index.html`：单页应用，包含登录页面和功能页面
  - `style.css`：页面样式（参考 Druid 风格）
  - `app.js`：前端逻辑（登录、API 调用、页面交互）

---

## 📦 功能模块设计

### 4.1 功能清单

#### 功能 1：字符串加密
- **输入**：明文字符串、表名（可选）、字段名（可选）、策略类名（可选）
- **处理**：
  1. 如果提供了表名和字段名，从 TableCache 获取策略
  2. 如果提供了策略类名，直接使用该策略
  3. 否则使用默认策略（从 StrategyCache 获取）
  4. 调用策略的 `encryption()` 方法
- **输出**：加密后的字符串
- **API**：`POST /monitor/api/encrypt.json`

#### 功能 2：字符串解密
- **输入**：加密字符串、表名（可选）、字段名（可选）、策略类名（可选）
- **处理**：
  1. 如果提供了表名和字段名，从 TableCache 获取策略
  2. 如果提供了策略类名，直接使用该策略
  3. 否则使用默认策略（从 StrategyCache 获取）
  4. 调用策略的 `decryption()` 方法
- **输出**：解密后的字符串
- **API**：`POST /monitor/api/decrypt.json`

### 4.2 数据流向

#### 加密功能数据流
```
前端输入 → POST /monitor/api/encrypt.json
    │
    ├─→ MonitorController 处理
    │
    ├─→ 检查 Session 认证
    │
    ├─→ 确定加密策略：
    │   ├─→ 如果提供了表名和字段名 → 从 TableCache 获取策略类
    │   ├─→ 如果提供了策略类名 → 直接使用该策略类
    │   └─→ 否则 → 从 StrategyCache 获取默认策略
    │
    ├─→ 从 StrategyCache 获取策略实例
    │
    ├─→ 调用策略的 encryption() 方法
    │
    └─→ 返回加密后的字符串（JSON 格式）
```

#### 解密功能数据流
```
前端输入 → POST /monitor/api/decrypt.json
    │
    ├─→ MonitorController 处理
    │
    ├─→ 检查 Session 认证
    │
    ├─→ 确定解密策略：
    │   ├─→ 如果提供了表名和字段名 → 从 TableCache 获取策略类
    │   ├─→ 如果提供了策略类名 → 直接使用该策略类
    │   └─→ 否则 → 从 StrategyCache 获取默认策略
    │
    ├─→ 从 StrategyCache 获取策略实例
    │
    ├─→ 调用策略的 decryption() 方法
    │
    └─→ 返回解密后的字符串（JSON 格式）
```

---

## 🔌 API 接口设计

### 5.1 接口列表

#### 认证相关
- `POST /monitor/login`：登录处理（表单提交）
- `GET /monitor/logout`：退出登录
- `GET /monitor/api/check.json`：检查登录状态

#### 功能接口（JSON）
- `POST /monitor/api/encrypt.json`：字符串加密
- `POST /monitor/api/decrypt.json`：字符串解密
- `GET /monitor/api/strategies.json`：获取可用策略列表

### 5.2 接口设计

#### 5.2.1 加密接口

**请求**：`POST /monitor/api/encrypt.json`
```json
{
  "text": "张三",                    // 要加密的文本（必填）
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
    "encrypted": "encrypted_value_here",
    "strategy": "com.example.Strategy"
  },
  "message": "加密成功"
}
```

#### 5.2.2 解密接口

**请求**：`POST /monitor/api/decrypt.json`
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
    "decrypted": "张三",
    "strategy": "com.example.Strategy"
  },
  "message": "解密成功"
}
```

#### 5.2.3 获取策略列表接口

**请求**：`GET /monitor/api/strategies.json`

**响应**：
```json
{
  "success": true,
  "data": {
    "defaultStrategy": "com.example.DefaultStrategy",
    "tableStrategies": [
      {
        "tableName": "user",
        "fields": [
          {
            "fieldName": "name",
            "strategy": "com.example.NameStrategy"
          },
          {
            "fieldName": "phone",
            "strategy": "com.example.PhoneStrategy"
          }
        ]
      }
    ]
  }
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
- 400：参数错误（如文本为空）
- 500：服务器内部错误（如策略不存在、加密解密失败）

---

## 🔐 安全设计

### 6.1 认证机制

#### 认证流程
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

#### 认证检查
- **对于静态资源请求**：如果未登录，重定向到登录页面
- **对于 JSON 接口请求**：如果未登录，返回 401 状态码

#### Session 存储
- 登录成功：`session.setAttribute("monitor_logged_in", true)`
- 检查登录：`session.getAttribute("monitor_logged_in") != null`

### 6.2 配置方式

```yaml
securtkit:
  monitor:
    enabled: true              # 是否启用监控页面
    username: admin            # 用户名（写死在配置中）
    password: admin123         # 密码（写死在配置中）
    path: /monitor            # 访问路径前缀
```

### 6.3 安全建议

1. **生产环境配置**：
   - 必须修改默认密码
   - 建议使用强密码
   - 可以通过环境变量配置密码

2. **访问控制**：
   - 建议限制访问 IP（通过 Nginx 或拦截器）
   - 可以添加 IP 白名单配置

3. **日志记录**：
   - 记录登录失败日志
   - 记录加密解密操作日志（可选，注意敏感信息）

---

## 🎨 前端页面设计

### 7.1 页面结构

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
    ├── 加密区域
    │   ├── 输入框（明文）
    │   ├── 表名/字段名/策略选择（可选）
    │   ├── 加密按钮
    │   └── 结果显示（密文）
    │
    └── 解密区域
        ├── 输入框（密文）
        ├── 表名/字段名/策略选择（可选）
        ├── 解密按钮
        └── 结果显示（明文）
```

### 7.2 页面布局（参考 Druid 风格）

```
┌─────────────────────────────────────────────────────────┐
│  Securt-Kit 加密解密工具                         [退出]  │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │ 字符串加密                                         │  │
│  ├───────────────────────────────────────────────────┤  │
│  │ 输入内容:                                         │  │
│  │ ┌─────────────────────────────────────────────┐ │  │
│  │ │                                               │ │  │
│  │ └─────────────────────────────────────────────┘ │  │
│  │                                                 │  │
│  │ 策略选择（可选）:                                │  │
│  │ 表名: [输入框]  字段名: [输入框]                 │  │
│  │ 或策略类名: [下拉框/输入框]                       │  │
│  │                                                 │  │
│  │ [加密] [清空]                                    │  │
│  │                                                 │  │
│  │ 加密结果:                                        │  │
│  │ ┌─────────────────────────────────────────────┐ │  │
│  │ │                                               │ │  │
│  │ └─────────────────────────────────────────────┘ │  │
│  │ [复制结果]                                       │  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │ 字符串解密                                         │  │
│  ├───────────────────────────────────────────────────┤  │
│  │ 输入内容:                                         │  │
│  │ ┌─────────────────────────────────────────────┐ │  │
│  │ │                                               │ │  │
│  │ └─────────────────────────────────────────────┘ │  │
│  │                                                 │  │
│  │ 策略选择（可选）:                                │  │
│  │ 表名: [输入框]  字段名: [输入框]                 │  │
│  │ 或策略类名: [下拉框/输入框]                       │  │
│  │                                                 │  │
│  │ [解密] [清空]                                    │  │
│  │                                                 │  │
│  │ 解密结果:                                        │  │
│  │ ┌─────────────────────────────────────────────┐ │  │
│  │ │                                               │ │  │
│  │ └─────────────────────────────────────────────┘ │  │
│  │ [复制结果]                                       │  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 7.3 前端交互流程

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
1. 用户输入明文和相关参数（可选）
2. 点击"加密"按钮
3. 调用 POST /monitor/api/encrypt.json
4. 显示加密结果
5. 可选：点击"复制结果"复制到剪贴板
```

#### 解密流程
```
1. 用户输入密文和相关参数（可选）
2. 点击"解密"按钮
3. 调用 POST /monitor/api/decrypt.json
4. 显示解密结果
5. 可选：点击"复制结果"复制到剪贴板
```

---

## ⚙️ 实现细节

### 8.1 Controller 实现代码

#### 8.1.1 Controller 主类

```java
package io.github.hexlodev.ui.monitor;

import io.github.hexlodev.core.TableCache;
import io.github.hexlodev.core.cache.StrategyCache;
import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 监控页面 Controller
 * 处理监控页面的所有请求（登录、静态资源、加密解密接口）
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("${securtkit.monitor.path:/monitor}")
@ConditionalOnProperty(prefix = "securtkit.monitor", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(MonitorProperties.class)
public class MonitorController {

    @Autowired
    private MonitorProperties properties;

    /**
     * 检查登录状态
     */
    private boolean checkLogin(HttpSession session) {
        return session != null && session.getAttribute("monitor_logged_in") != null;
    }

    /**
     * 设置登录状态
     */
    private void setLogin(HttpSession session) {
        session.setAttribute("monitor_logged_in", true);
    }

    /**
     * 清除登录状态
     */
    private void clearLogin(HttpSession session) {
        if (session != null) {
            session.removeAttribute("monitor_logged_in");
        }
    }

    /**
     * 处理登录页面和主页面
     */
    @GetMapping(value = {"/", "/index.html"})
    public void index(HttpServletRequest request, HttpServletResponse response, HttpSession session) 
            throws IOException {
        // 检查登录状态
        if (!checkLogin(session)) {
            // 未登录，重定向到登录页面（实际返回登录页面 HTML）
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(getLoginPageHtml());
            return;
        }
        
        // 已登录，返回主页面
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(getMainPageHtml());
    }

    /**
     * 处理登录请求
     */
    @PostMapping("/login")
    public ApiResponse<?> login(@RequestParam String username, 
                                @RequestParam String password,
                                HttpSession session) {
        // 验证用户名密码
        if (properties.getUsername().equals(username) && 
            properties.getPassword().equals(password)) {
            setLogin(session);
            log.info("Monitor login success: {}", username);
            return ApiResponse.success("登录成功");
        } else {
            log.warn("Monitor login failed: {}", username);
            return ApiResponse.error("用户名或密码错误");
        }
    }

    /**
     * 处理退出登录
     */
    @GetMapping("/logout")
    public void logout(HttpSession session, HttpServletResponse response) throws IOException {
        clearLogin(session);
        response.sendRedirect(properties.getPath() + "/");
    }

    /**
     * 检查登录状态接口
     */
    @GetMapping("/api/check.json")
    public ApiResponse<Map<String, Object>> checkLogin(HttpSession session) {
        Map<String, Object> data = new HashMap<>();
        data.put("loggedIn", checkLogin(session));
        return ApiResponse.success(data);
    }

    /**
     * 加密接口
     */
    @PostMapping("/api/encrypt.json")
    public ApiResponse<EncryptResponse> encrypt(@RequestBody EncryptRequest request,
                                                HttpSession session) {
        // 检查登录
        if (!checkLogin(session)) {
            return ApiResponse.error(401, "未登录");
        }

        try {
            // 验证参数
            if (request.getText() == null || request.getText().trim().isEmpty()) {
                return ApiResponse.error("文本内容不能为空");
            }

            // 确定加密策略
            FieldEncryptorStrategy strategy = determineStrategy(request);
            if (strategy == null) {
                return ApiResponse.error("无法确定加密策略，请提供表名+字段名或策略类名");
            }

            // 执行加密
            String encrypted = strategy.encryption(request.getText());

            // 返回结果
            EncryptResponse response = new EncryptResponse();
            response.setOriginal(request.getText());
            response.setEncrypted(encrypted);
            response.setStrategy(strategy.getClass().getName());
            return ApiResponse.success(response, "加密成功");

        } catch (Exception e) {
            log.error("加密失败", e);
            return ApiResponse.error("加密失败: " + e.getMessage());
        }
    }

    /**
     * 解密接口
     */
    @PostMapping("/api/decrypt.json")
    public ApiResponse<DecryptResponse> decrypt(@RequestBody DecryptRequest request,
                                                HttpSession session) {
        // 检查登录
        if (!checkLogin(session)) {
            return ApiResponse.error(401, "未登录");
        }

        try {
            // 验证参数
            if (request.getText() == null || request.getText().trim().isEmpty()) {
                return ApiResponse.error("文本内容不能为空");
            }

            // 确定解密策略
            FieldEncryptorStrategy strategy = determineStrategy(request);
            if (strategy == null) {
                return ApiResponse.error("无法确定解密策略，请提供表名+字段名或策略类名");
            }

            // 执行解密
            String decrypted = strategy.decryption(request.getText());

            // 返回结果
            DecryptResponse response = new DecryptResponse();
            response.setEncrypted(request.getText());
            response.setDecrypted(decrypted);
            response.setStrategy(strategy.getClass().getName());
            return ApiResponse.success(response, "解密成功");

        } catch (Exception e) {
            log.error("解密失败", e);
            return ApiResponse.error("解密失败: " + e.getMessage());
        }
    }

    /**
     * 获取可用策略列表
     */
    @GetMapping("/api/strategies.json")
    public ApiResponse<StrategiesResponse> getStrategies(HttpSession session) {
        // 检查登录
        if (!checkLogin(session)) {
            return ApiResponse.error(401, "未登录");
        }

        try {
            StrategiesResponse response = new StrategiesResponse();
            
            // 获取默认策略
            FieldEncryptorStrategy defaultStrategy = StrategyCache.getStrategy(FieldEncryptorStrategy.class);
            if (defaultStrategy != null) {
                response.setDefaultStrategy(defaultStrategy.getClass().getName());
            }

            // 获取表策略
            Map<String, Map<String, String>> tableStrategies = new HashMap<>();
            for (String tableName : TableCache.getTables()) {
                Map<String, Class<? extends FieldEncryptorStrategy>> fieldMap = 
                    TableCache.getTableFieldEncryptInfo(tableName);
                if (fieldMap != null && !fieldMap.isEmpty()) {
                    Map<String, String> fields = new HashMap<>();
                    fieldMap.forEach((fieldName, strategyClass) -> 
                        fields.put(fieldName, strategyClass.getName()));
                    tableStrategies.put(tableName, fields);
                }
            }
            response.setTableStrategies(tableStrategies);

            return ApiResponse.success(response);

        } catch (Exception e) {
            log.error("获取策略列表失败", e);
            return ApiResponse.error("获取策略列表失败: " + e.getMessage());
        }
    }

    /**
     * 确定使用的策略
     */
    private FieldEncryptorStrategy determineStrategy(Object request) {
        String strategyClassName = null;
        String tableName = null;
        String fieldName = null;

        if (request instanceof EncryptRequest) {
            EncryptRequest req = (EncryptRequest) request;
            strategyClassName = req.getStrategy();
            tableName = req.getTableName();
            fieldName = req.getFieldName();
        } else if (request instanceof DecryptRequest) {
            DecryptRequest req = (DecryptRequest) request;
            strategyClassName = req.getStrategy();
            tableName = req.getTableName();
            fieldName = req.getFieldName();
        }

        Class<? extends FieldEncryptorStrategy> strategyClass = null;

        // 优先级1: 直接指定的策略类名
        if (strategyClassName != null && !strategyClassName.trim().isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends FieldEncryptorStrategy> clazz = 
                    (Class<? extends FieldEncryptorStrategy>) Class.forName(strategyClassName);
                strategyClass = clazz;
            } catch (Exception e) {
                log.warn("无法加载策略类: {}", strategyClassName, e);
                return null;
            }
        }
        // 优先级2: 通过表名和字段名获取策略
        else if (tableName != null && !tableName.trim().isEmpty() && 
                 fieldName != null && !fieldName.trim().isEmpty()) {
            strategyClass = TableCache.getTableFieldEncryptInfo(tableName, fieldName);
        }
        // 优先级3: 使用默认策略
        else {
            try {
                FieldEncryptorStrategy defaultStrategy = StrategyCache.getStrategy(FieldEncryptorStrategy.class);
                if (defaultStrategy != null) {
                    return defaultStrategy;
                }
            } catch (Exception e) {
                log.warn("无法获取默认策略", e);
            }
        }

        // 获取策略实例
        if (strategyClass != null) {
            return StrategyCache.getStrategy(strategyClass);
        }

        return null;
    }

    /**
     * 获取登录页面 HTML（简化版，实际应该从 resources/static 读取）
     */
    private String getLoginPageHtml() {
        return "<!DOCTYPE html><html><head><title>登录</title></head><body>" +
               "<h1>Securt-Kit 监控页面</h1>" +
               "<form method='post' action='" + properties.getPath() + "/login'>" +
               "<input type='text' name='username' placeholder='用户名'><br>" +
               "<input type='password' name='password' placeholder='密码'><br>" +
               "<button type='submit'>登录</button>" +
               "</form></body></html>";
    }

    /**
     * 获取主页面 HTML（简化版，实际应该从 resources/static 读取）
     */
    private String getMainPageHtml() {
        // 实际应该读取 resources/static/monitor/index.html
        return "<!DOCTYPE html><html><head><title>监控页面</title></head><body>" +
               "<h1>Securt-Kit 加密解密工具</h1>" +
               "<p>实际内容应该从 resources/static/monitor/index.html 读取</p>" +
               "</body></html>";
    }
}
```

#### 8.1.2 请求响应类

```java
// 加密请求
@Data
public class EncryptRequest {
    private String text;          // 必填
    private String tableName;     // 可选
    private String fieldName;     // 可选
    private String strategy;      // 可选
}

// 解密请求
@Data
public class DecryptRequest {
    private String text;          // 必填
    private String tableName;     // 可选
    private String fieldName;     // 可选
    private String strategy;      // 可选
}

// 加密响应
@Data
public class EncryptResponse {
    private String original;
    private String encrypted;
    private String strategy;
}

// 解密响应
@Data
public class DecryptResponse {
    private String encrypted;
    private String decrypted;
    private String strategy;
}

// 策略列表响应
@Data
public class StrategiesResponse {
    private String defaultStrategy;
    private Map<String, Map<String, String>> tableStrategies; // tableName -> (fieldName -> strategy)
}

// 统一响应格式
@Data
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private Integer code;

    public static <T> ApiResponse<T> success(T data) {
        return success(data, "操作成功");
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setData(data);
        response.setMessage(message);
        response.setCode(200);
        return response;
    }

    public static <T> ApiResponse<T> error(String message) {
        return error(500, message);
    }

    public static <T> ApiResponse<T> error(Integer code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage(message);
        response.setCode(code);
        return response;
    }
}
```

### 8.2 加密解密逻辑

#### 加密逻辑
```
1. 从请求 JSON 中提取参数（text, tableName, fieldName, strategy）
2. 验证 text 不为空
3. 确定加密策略：
   - 如果提供了 strategy 参数 → 使用指定的策略类
   - 否则，如果提供了 tableName 和 fieldName → 从 TableCache 获取策略
   - 否则，使用默认策略（从 StrategyCache 获取）
4. 从 StrategyCache 获取策略实例
5. 调用策略的 encryption() 方法
6. 返回加密结果
```

#### 解密逻辑
```
1. 从请求 JSON 中提取参数（text, tableName, fieldName, strategy）
2. 验证 text 不为空
3. 确定解密策略：
   - 如果提供了 strategy 参数 → 使用指定的策略类
   - 否则，如果提供了 tableName 和 fieldName → 从 TableCache 获取策略
   - 否则，使用默认策略（从 StrategyCache 获取）
4. 从 StrategyCache 获取策略实例
5. 调用策略的 decryption() 方法
6. 返回解密结果
```

### 8.3 配置属性类

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

### 8.4 自动配置

```java
package io.github.hexlodev.ui.monitor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 监控页面自动配置
 * 
 * @author hexlodev
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(prefix = "securtkit.monitor", 
                      name = "enabled", 
                      havingValue = "true",
                      matchIfMissing = false)
@EnableConfigurationProperties(MonitorProperties.class)
public class MonitorAutoConfiguration {
    // Controller 通过 @RestController 自动注册，不需要手动 @Bean
    // 只需要确保 MonitorProperties 被正确加载即可
}
```

### 8.5 依赖配置

#### 8.5.1 securt-kit-ui 模块 pom.xml

```xml
<dependencies>
    <!-- 依赖 starter 模块（包含核心模块和 Spring Boot 自动配置） -->
    <dependency>
        <groupId>io.github.hexlodev</groupId>
        <artifactId>securt-kit-starter</artifactId>
        <version>${project.version}</version>
    </dependency>

    <!-- Spring Boot Web（provided scope，由用户提供） -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

**说明**：
- `spring-boot-starter-web` 使用 `provided` scope，因为：
  - 用户在使用监控页面时，肯定已经引入了 Spring Boot Web
  - 避免版本冲突，使用用户项目中的版本
  - 保持低依赖原则（用户不引入 Web 时，监控页面不可用是合理的）

---

## 📋 实现要点总结

### 9.1 核心设计原则

1. ✅ **统一 Controller**：一个 Controller 处理所有请求（登录、静态资源、JSON 接口）
2. ✅ **URL 后缀区分**：`.json` = 数据接口，其他 = 静态资源
3. ✅ **直接调用核心组件**：不需要 Service 层，直接调用 TableCache、StrategyCache
4. ✅ **Session 认证**：简单实用，用户名密码从配置读取
5. ✅ **静态资源内嵌**：HTML/CSS/JS 放在 `resources/static/monitor/`

### 9.2 关键技术点

1. **Controller 实现**：
   - 使用 `@RestController` 和 `@RequestMapping` 处理请求
   - 使用 `@RequestBody` 自动解析 JSON 请求参数
   - 使用 `@Autowired` 自动注入依赖（如 MonitorProperties）
   - 使用 `@PostMapping`、`@GetMapping` 等注解明确路由
   - JSON 接口直接调用核心组件并返回，不需要 Service 层
   - Spring 自动处理 JSON 序列化/反序列化

2. **认证处理**：
   - 登录：表单提交到 `/monitor/login`，验证后设置 Session
   - 检查：拦截器或 Controller 方法检查 Session，未登录返回 401 或重定向

3. **策略获取**：
   - 从 TableCache 获取表字段策略：`TableCache.getTableFieldEncryptInfo(tableName, fieldName)`
   - 从 StrategyCache 获取策略实例：`StrategyCache.getStrategy(strategyClass)`
   - 直接调用策略方法：`strategy.encryption(text)` 或 `strategy.decryption(text)`

4. **前端实现**：
   - 单页应用，JavaScript 控制显示/隐藏
   - 使用 Fetch API 调用 JSON 接口
   - 登录后保存状态，页面刷新时检查登录状态

### 9.3 注意事项

1. **依赖最小化**：
   - 只依赖核心模块的 TableCache、StrategyCache、FieldEncryptorStrategy
   - 依赖 Spring MVC（但使用 `provided` scope，由用户提供）
   - 不依赖业务代码
   - 监控页面是可选功能，用户不使用时不引入该模块即可

2. **安全性**：
   - 生产环境必须修改默认密码
   - 建议限制访问 IP（通过 Nginx 或拦截器）
   - 记录操作日志（注意敏感信息）

3. **错误处理**：
   - 统一错误响应格式
   - 前端显示友好的错误信息
   - 记录错误日志便于排查

---

**设计方案完成时间**：2025-01-XX  
**参考实现**：Druid StatViewServlet（理念参考，但使用 Spring MVC Controller 实现）  
**技术选型**：使用 Spring MVC Controller 而非 Servlet（详见 `SERVLET-VS-CONTROLLER-COMPARISON.md`）  
**版本**：v1.0

