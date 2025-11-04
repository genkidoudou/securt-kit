# Druid 监控页面实现分析

> 分析 Druid 监控页面的实现机制，为 Securt-Kit 监控页面设计提供参考

---

## 📋 目录

1. [整体架构](#整体架构)
2. [核心组件](#核心组件)
3. [静态资源处理](#静态资源处理)
4. [认证机制](#认证机制)
5. [数据收集机制](#数据收集机制)
6. [Spring Boot 集成](#spring-boot-集成)
7. [关键实现细节](#关键实现细节)
8. [可借鉴的设计](#可借鉴的设计)

---

## 🏗️ 整体架构

### 1.1 架构概览

```
┌─────────────────────────────────────────────────┐
│            Druid 监控页面架构                      │
├─────────────────────────────────────────────────┤
│                                                 │
│  ┌──────────────────────────────────────────┐  │
│  │  浏览器请求: /druid/index.html            │  │
│  └──────────────────┬───────────────────────┘  │
│                     │                           │
│                     ▼                           │
│  ┌──────────────────────────────────────────┐  │
│  │  StatViewServlet (处理监控页面请求)        │  │
│  │    - 提供静态 HTML/CSS/JS                 │  │
│  │    - 处理登录认证                         │  │
│  │    - 提供 JSON API 接口                   │  │
│  └──────────────────┬───────────────────────┘  │
│                     │                           │
│                     ▼                           │
│  ┌──────────────────────────────────────────┐  │
│  │  DruidDataSource (数据源)                 │  │
│  │    - 维护连接池统计信息                   │  │
│  │    - 维护 SQL 执行统计信息                │  │
│  └──────────────────┬───────────────────────┘  │
│                     │                           │
│                     ▼                           │
│  ┌──────────────────────────────────────────┐  │
│  │  StatFilter (过滤器)                      │  │
│  │    - 拦截 SQL 执行                        │  │
│  │    - 收集统计信息                         │  │
│  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

### 1.2 请求流程

```
1. 用户访问 /druid/index.html
   │
   ├─→ StatViewServlet 拦截请求
   │
   ├─→ 检查是否已登录（Session 或 Basic Auth）
   │
   ├─→ 如果未登录，返回登录页面
   │
   ├─→ 如果已登录，返回监控页面 HTML
   │
   └─→ 页面加载后，通过 AJAX 请求 /druid/*.json 获取数据
```

---

## 🔧 核心组件

### 2.1 StatViewServlet

**作用**：
- 提供监控页面的静态资源（HTML/CSS/JS）
- 处理登录认证
- 提供 JSON API 接口返回统计数据

**关键特性**：
1. **静态资源内嵌**：HTML/CSS/JS 文件作为资源文件打包在 jar 中
2. **Servlet 映射**：通过 `ServletRegistrationBean` 注册，映射到 `/druid/*`
3. **认证处理**：支持 Basic Auth 和 Session 认证

**实现方式**：
```java
public class StatViewServlet extends ResourceServlet {
    
    // 静态资源路径
    private static final String RESOURCE_PATH = "support/http/resources";
    
    @Override
    protected void process(HttpServletRequest request, 
                          HttpServletResponse response) 
        throws ServletException, IOException {
        
        String contextPath = request.getContextPath();
        String requestURI = request.getRequestURI();
        
        // 处理登录
        if (isLoginPage(requestURI)) {
            processLogin(request, response);
            return;
        }
        
        // 检查认证
        if (!isAuthorized(request)) {
            response.sendRedirect(contextPath + "/druid/login.html");
            return;
        }
        
        // 处理静态资源或 JSON API
        if (requestURI.endsWith(".json")) {
            processJson(request, response);
        } else {
            processResource(request, response);
        }
    }
    
    // 从 jar 包中读取静态资源
    protected String getFilePath(String fileName) {
        return RESOURCE_PATH + "/" + fileName;
    }
}
```

### 2.2 StatFilter

**作用**：
- 拦截所有 SQL 执行
- 收集 SQL 统计信息（执行次数、耗时、错误等）
- 将统计数据存储到 DruidDataSource 的 StatContext 中

**实现方式**：
```java
public class StatFilter extends FilterEventAdapter {
    
    @Override
    protected void statementExecuteUpdateBefore(StatementProxy statement, String sql) {
        // 记录 SQL 开始执行
        statement.setLastExecuteStartNano();
    }
    
    @Override
    protected void statementExecuteUpdateAfter(StatementProxy statement, String sql, int updateCount) {
        // 记录 SQL 执行完成
        long nanos = System.nanoTime() - statement.getLastExecuteStartNano();
        // 更新统计信息
        updateStatistics(sql, nanos, false);
    }
    
    @Override
    protected void statementExecuteQueryAfter(StatementProxy statement, String sql, ResultSetProxy result) {
        // 记录查询完成
        long nanos = System.nanoTime() - statement.getLastExecuteStartNano();
        updateStatistics(sql, nanos, false);
    }
}
```

### 2.3 ResourceServlet（基类）

**作用**：
- 提供从 jar 包读取静态资源的通用功能
- 处理资源文件的读取和输出

**关键方法**：
```java
public abstract class ResourceServlet extends HttpServlet {
    
    // 从类路径读取资源文件
    protected void returnResourceFile(String fileName, String uri, HttpServletResponse response)
        throws ServletException, IOException {
        
        String filePath = getFilePath(fileName);
        InputStream resourceStream = getClass().getClassLoader()
            .getResourceAsStream(filePath);
        
        if (resourceStream == null) {
            response.sendError(404);
            return;
        }
        
        // 设置 Content-Type
        String contentType = getContentType(fileName);
        response.setContentType(contentType);
        
        // 输出文件内容
        byte[] buffer = new byte[1024];
        int len;
        while ((len = resourceStream.read(buffer)) > 0) {
            response.getOutputStream().write(buffer, 0, len);
        }
    }
}
```

---

## 📦 静态资源处理

### 3.1 资源文件位置

Druid 的静态资源文件打包在 jar 包中：

```
druid-xxx.jar
└── META-INF/resources/
    └── druid/
        ├── index.html
        ├── login.html
        ├── sql.html
        ├── datasource.html
        ├── css/
        │   └── style.css
        └── js/
            └── app.js
```

### 3.2 资源读取方式

**方式 1：通过 ClassLoader 读取**
```java
InputStream is = getClass().getClassLoader()
    .getResourceAsStream("META-INF/resources/druid/index.html");
```

**方式 2：通过 ServletContext 读取（Spring Boot）**
```java
// Spring Boot 会自动处理 META-INF/resources/ 下的静态资源
// 可以直接通过 /druid/index.html 访问
```

### 3.3 Spring Boot 自动处理

Spring Boot 会自动处理 `META-INF/resources/` 目录下的静态资源：

```java
// Spring Boot 的 ResourceHandlerRegistry 会自动注册
// META-INF/resources/ 作为静态资源路径
```

---

## 🔐 认证机制

### 4.1 认证方式

Druid 支持两种认证方式：

#### 方式 1：Basic Authentication（HTTP Basic Auth）

```java
// 在 StatViewServlet 中检查 Authorization header
String authHeader = request.getHeader("Authorization");
if (authHeader != null && authHeader.startsWith("Basic ")) {
    String credentials = new String(
        Base64.getDecoder().decode(authHeader.substring(6))
    );
    String[] parts = credentials.split(":");
    if (parts.length == 2) {
        String username = parts[0];
        String password = parts[1];
        if (isValidUser(username, password)) {
            return true;
        }
    }
}
```

#### 方式 2：Session 认证（推荐）

```java
// 登录页面提交表单
if (request.getMethod().equals("POST")) {
    String username = request.getParameter("username");
    String password = request.getParameter("password");
    
    if (isValidUser(username, password)) {
        HttpSession session = request.getSession();
        session.setAttribute("druid_user", username);
        response.sendRedirect("/druid/index.html");
        return;
    }
}

// 检查 Session
HttpSession session = request.getSession(false);
if (session != null && session.getAttribute("druid_user") != null) {
    return true; // 已登录
}
```

### 4.2 配置方式

```yaml
spring:
  datasource:
    druid:
      stat-view-servlet:
        enabled: true
        url-pattern: /druid/*
        login-username: admin      # 用户名
        login-password: admin123   # 密码
        allow: 127.0.0.1          # IP 白名单
        deny: 192.168.1.100       # IP 黑名单
        reset-enable: false        # 是否允许重置统计
```

### 4.3 Spring Boot 自动配置

```java
@Configuration
@ConditionalOnWebApplication
@ConditionalOnProperty(name = "spring.datasource.druid.stat-view-servlet.enabled", 
                       havingValue = "true", matchIfMissing = true)
public class DruidStatViewServletConfiguration {
    
    @Bean
    public ServletRegistrationBean<StatViewServlet> statViewServletRegistrationBean(
            DruidStatProperties properties) {
        
        DruidStatProperties.StatViewServlet config = properties.getStatViewServlet();
        
        ServletRegistrationBean<StatViewServlet> registration = 
            new ServletRegistrationBean<>(new StatViewServlet(), config.getUrlPattern());
        
        // 设置初始化参数
        registration.addInitParameter("loginUsername", config.getLoginUsername());
        registration.addInitParameter("loginPassword", config.getLoginPassword());
        registration.addInitParameter("allow", config.getAllow());
        registration.addInitParameter("deny", config.getDeny());
        
        return registration;
    }
}
```

---

## 📊 数据收集机制

### 5.1 统计数据存储

Druid 的统计数据存储在 `DruidDataSource` 的 `StatContext` 中：

```java
public class DruidDataSource extends DruidAbstractDataSource {
    
    // SQL 统计信息
    private final Map<String, SQLStat> sqlStatMap = new ConcurrentHashMap<>();
    
    // 连接池统计信息
    private final AtomicLong connectCount = new AtomicLong();
    private final AtomicLong closeCount = new AtomicLong();
    private final AtomicLong activeCount = new AtomicLong();
    
    // 获取 SQL 统计信息
    public Map<String, SQLStat> getSqlStatMap() {
        return sqlStatMap;
    }
}
```

### 5.2 JSON API 接口

StatViewServlet 提供多个 JSON 接口返回统计数据：

```
GET /druid/dataSource.json          # 数据源统计
GET /druid/sql.json                  # SQL 统计
GET /druid/connection.json            # 连接统计
GET /druid/activeConnectionStackTrace.json  # 活跃连接堆栈
```

**实现示例**：
```java
if (uri.endsWith("/sql.json")) {
    response.setContentType("application/json;charset=utf-8");
    Map<String, SQLStat> sqlStatMap = dataSource.getSqlStatMap();
    
    List<Map<String, Object>> sqlList = new ArrayList<>();
    for (SQLStat stat : sqlStatMap.values()) {
        Map<String, Object> item = new HashMap<>();
        item.put("sql", stat.getSql());
        item.put("executeCount", stat.getExecuteCount());
        item.put("executeTime", stat.getExecuteTime());
        item.put("errorCount", stat.getErrorCount());
        sqlList.add(item);
    }
    
    String json = JSON.toJSONString(sqlList);
    response.getWriter().write(json);
}
```

---

## 🔗 Spring Boot 集成

### 6.1 自动配置类

Druid 的 Spring Boot Starter 提供了自动配置：

```java
@Configuration
@ConditionalOnClass({DruidDataSource.class, StatViewServlet.class})
@EnableConfigurationProperties({DruidStatProperties.class})
public class DruidDataSourceAutoConfigure {
    
    @Bean
    @ConditionalOnProperty(name = "spring.datasource.druid.stat-view-servlet.enabled", 
                          havingValue = "true", matchIfMissing = true)
    public ServletRegistrationBean<StatViewServlet> statViewServlet() {
        // 注册 StatViewServlet
    }
    
    @Bean
    @ConditionalOnProperty(name = "spring.datasource.druid.web-stat-filter.enabled",
                          havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<WebStatFilter> webStatFilter() {
        // 注册 WebStatFilter
    }
    
    @Bean
    @ConditionalOnProperty(name = "spring.datasource.druid.filter.stat.enabled",
                          havingValue = "true", matchIfMissing = true)
    public StatFilter statFilter() {
        // 注册 StatFilter
    }
}
```

### 6.2 配置属性类

```java
@ConfigurationProperties(prefix = "spring.datasource.druid")
public class DruidStatProperties {
    
    @NestedConfigurationProperty
    private StatViewServlet statViewServlet = new StatViewServlet();
    
    @NestedConfigurationProperty
    private WebStatFilter webStatFilter = new WebStatFilter();
    
    @NestedConfigurationProperty
    private StatFilter filter = new StatFilter();
    
    // getters and setters
}
```

---

## 🎯 关键实现细节

### 7.1 StatViewServlet 的核心实现

**重要说明**：Druid **不是通过 Spring MVC Controller 来暴露接口**，而是直接在 Servlet 中处理所有请求（静态资源 + 数据接口）。

**核心原理**：
- StatViewServlet 继承自 `ResourceServlet`（继承自 `HttpServlet`）
- 统一处理所有 `/druid/*` 路径的请求
- 通过 URL 后缀判断：`.json` 结尾 = 数据接口，其他 = 静态资源

**关键代码结构**：
```java
public class StatViewServlet extends ResourceServlet {
    
    @Override
    protected void process(HttpServletRequest request, 
                          HttpServletResponse response) 
        throws ServletException, IOException {
        
        String uri = request.getRequestURI();
        
        // 1. 处理登录页面
        if (isLoginPage(uri)) {
            processLogin(request, response);
            return;
        }
        
        // 2. 检查认证
        if (!isAuthorized(request)) {
            response.sendRedirect("/druid/login.html");
            return;
        }
        
        // 3. 判断请求类型
        if (uri.endsWith(".json")) {
            // JSON 数据接口：直接从 DruidDataSource 读取数据
            processJson(request, response);
        } else {
            // 静态资源：返回 HTML/CSS/JS 文件
            returnResourceFile(getFileName(uri), response);
        }
    }
    
    // 处理 JSON 数据请求
    protected void processJson(HttpServletRequest request, 
                              HttpServletResponse response) 
        throws IOException {
        
        String uri = request.getRequestURI();
        response.setContentType("application/json;charset=utf-8");
        
        // 直接从 DruidDataSource 获取数据，不需要 Service 层
        DruidDataSource dataSource = getDruidDataSource();
        
        if (uri.endsWith("/sql.json")) {
            // 直接读取 SQL 统计数据
            Map<String, SQLStat> sqlStatMap = dataSource.getSqlStatMap();
            // 转换为 JSON 返回
            writeJson(response, sqlStatMap);
        } else if (uri.endsWith("/dataSource.json")) {
            // 直接读取数据源统计
            // ...
        }
    }
    
    // 返回静态资源文件
    protected void returnResourceFile(String fileName, HttpServletResponse response) 
        throws IOException {
        
        String filePath = "META-INF/resources/druid/" + fileName;
        InputStream is = getClass().getClassLoader().getResourceAsStream(filePath);
        
        if (is == null) {
            response.sendError(404);
            return;
        }
        
        // 设置 Content-Type
        if (fileName.endsWith(".html")) {
            response.setContentType("text/html;charset=utf-8");
        } else if (fileName.endsWith(".css")) {
            response.setContentType("text/css;charset=utf-8");
        } else if (fileName.endsWith(".js")) {
            response.setContentType("application/javascript;charset=utf-8");
        }
        
        // 输出文件内容
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) > 0) {
            response.getOutputStream().write(buffer, 0, len);
        }
        is.close();
    }
}
```

**关键点**：
1. ✅ **一个 Servlet 处理所有请求**：既处理静态资源，也处理数据接口
2. ✅ **通过 URL 后缀区分**：`.json` = 数据接口，其他 = 静态资源
3. ✅ **直接读取数据**：不需要 Service 层，直接从 DruidDataSource 读取
4. ✅ **不是 RESTful Controller**：不使用 Spring MVC 的 `@Controller` 和 `@RequestMapping`

### 7.2 Session 认证处理

**关键点**：
1. 登录页面提交表单到同一个 Servlet
2. 验证用户名密码后设置 Session
3. 其他请求检查 Session 是否存在

**实现示例**：
```java
protected void processLogin(HttpServletRequest request, 
                           HttpServletResponse response) 
    throws IOException {
    
    String method = request.getMethod();
    
    if ("GET".equals(method)) {
        // 显示登录页面
        returnResourceFile("login.html", response);
        return;
    }
    
    if ("POST".equals(method)) {
        // 处理登录
        String username = request.getParameter("loginUsername");
        String password = request.getParameter("loginPassword");
        
        String configUsername = getInitParameter("loginUsername");
        String configPassword = getInitParameter("loginPassword");
        
        if (configUsername.equals(username) && 
            configPassword.equals(password)) {
            HttpSession session = request.getSession();
            session.setAttribute("druid_user", username);
            response.sendRedirect(request.getContextPath() + "/druid/index.html");
        } else {
            response.sendRedirect(request.getContextPath() + "/druid/login.html?error=1");
        }
    }
}

protected boolean isAuthorized(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    return session != null && session.getAttribute("druid_user") != null;
}
```

### 7.3 JSON API 处理

**关键点**：
1. 识别 `.json` 结尾的请求
2. 从数据源获取统计数据
3. 转换为 JSON 格式返回

**实现示例**：
```java
protected void processJson(HttpServletRequest request, 
                          HttpServletResponse response) 
    throws IOException {
    
    String uri = request.getRequestURI();
    response.setContentType("application/json;charset=utf-8");
    
    if (uri.endsWith("/sql.json")) {
        // 返回 SQL 统计
        Map<String, SQLStat> sqlStatMap = dataSource.getSqlStatMap();
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (SQLStat stat : sqlStatMap.values()) {
            Map<String, Object> item = new HashMap<>();
            item.put("sql", stat.getSql());
            item.put("executeCount", stat.getExecuteCount());
            item.put("executeTime", stat.getExecuteTime());
            result.add(item);
        }
        
        String json = JSON.toJSONString(result);
        response.getWriter().write(json);
    }
    // ... 其他接口
}
```

---

## 💡 可借鉴的设计

### 8.1 静态资源处理

**借鉴点**：
1. ✅ **资源文件内嵌**：将 HTML/CSS/JS 打包在 jar 中
2. ✅ **路径映射**：通过 Servlet 或 Controller 处理静态资源
3. ✅ **Spring Boot 自动处理**：利用 `META-INF/resources/` 目录

**实现建议**：
```java
// 方式 1：使用 Controller 返回静态资源
@Controller
@RequestMapping("/monitor")
public class MonitorController {
    
    @GetMapping("/index.html")
    public void index(HttpServletResponse response) throws IOException {
        InputStream is = getClass().getClassLoader()
            .getResourceAsStream("static/monitor/index.html");
        // 读取并输出
    }
}

// 方式 2：直接放在 resources/static/monitor/ 目录
// Spring Boot 会自动处理，直接访问 /monitor/index.html
```

### 8.2 认证机制

**借鉴点**：
1. ✅ **简单 Session 认证**：使用 Session 存储登录状态
2. ✅ **配置化用户名密码**：从配置文件读取
3. ✅ **拦截器检查**：API 接口通过拦截器检查认证

**实现建议**：
```java
// 登录处理
@PostMapping("/login")
public String login(@RequestParam String username, 
                   @RequestParam String password,
                   HttpSession session) {
    if (config.getUsername().equals(username) && 
        config.getPassword().equals(password)) {
        session.setAttribute("monitor_logged_in", true);
        return "redirect:/monitor/index.html";
    }
    return "redirect:/monitor/index.html?error=1";
}

// 拦截器检查
public class MonitorAuthInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) {
        HttpSession session = request.getSession(false);
        if (session == null || 
            session.getAttribute("monitor_logged_in") == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }
}
```

### 8.3 数据收集和展示

**Druid 的实际实现方式**：
- ❌ **不是通过 Spring MVC Controller 暴露接口**
- ✅ **直接在 Servlet 中处理请求**：通过 URL 后缀（.json）判断是数据接口还是静态资源
- ✅ **直接读取数据**：从 DruidDataSource 的内存中直接读取，不需要 Service 层

**可借鉴的实现方式**：

**方式 A：类似 Druid（推荐）** - 一个 Servlet/Controller 统一处理
```java
@Controller
@RequestMapping("/monitor")
public class MonitorController {
    
    @GetMapping("/**")
    public void handleRequest(HttpServletRequest request, 
                             HttpServletResponse response) 
        throws IOException {
        
        String uri = request.getRequestURI();
        
        // 检查认证
        if (!isAuthorized(request)) {
            if (uri.endsWith(".json")) {
                response.setStatus(401);
            } else {
                response.sendRedirect("/monitor/login.html");
            }
            return;
        }
        
        // 判断请求类型
        if (uri.endsWith(".json")) {
            // JSON 数据接口：直接从内存读取数据
            processJson(request, response);
        } else {
            // 静态资源：返回 HTML/CSS/JS（或转发到 Spring Boot 的静态资源处理）
            // Spring Boot 会自动处理 resources/static/ 下的文件
        }
    }
    
    private void processJson(HttpServletRequest request, 
                            HttpServletResponse response) 
        throws IOException {
        
        String uri = request.getRequestURI();
        response.setContentType("application/json;charset=utf-8");
        
        if (uri.endsWith("/config.json")) {
            // 直接从 TableCache、SqlParseCache 读取数据
            Map<String, Object> data = new HashMap<>();
            data.put("tables", TableCache.getTables());
            data.put("cache", getCacheStats());
            
            response.getWriter().write(JSON.toJSONString(data));
        }
    }
}
```

**方式 B：Spring Boot 方式** - 分离静态资源和 API 接口
```java
// 静态资源：直接放在 resources/static/monitor/，Spring Boot 自动处理
// API 接口：使用 @RestController
@RestController
@RequestMapping("/monitor/api")
public class MonitorApiController {
    
    @GetMapping("/config")
    public ResponseEntity<?> getConfig(HttpSession session) {
        if (!isAuthorized(session)) {
            return ResponseEntity.status(401).build();
        }
        
        // 直接从内存读取数据
        Map<String, Object> data = new HashMap<>();
        data.put("tables", TableCache.getTables());
        data.put("cache", getCacheStats());
        
        return ResponseEntity.ok(data);
    }
}
```

**推荐方式 A**：更接近 Druid 的实现方式，统一处理，代码更简洁。

### 8.4 Spring Boot 集成

**借鉴点**：
1. ✅ **自动配置类**：通过 `@ConditionalOnProperty` 控制启用
2. ✅ **配置属性类**：使用 `@ConfigurationProperties` 绑定配置
3. ✅ **Servlet/Filter 注册**：通过 `ServletRegistrationBean` 注册

**实现建议**：
```java
@Configuration
@ConditionalOnProperty(prefix = "securtkit.monitor", 
                      name = "enabled", 
                      havingValue = "true",
                      matchIfMissing = false)
@EnableConfigurationProperties(MonitorProperties.class)
public class MonitorAutoConfiguration {
    
    @Bean
    public MonitorController monitorController() {
        return new MonitorController();
    }
    
    @Bean
    public MonitorAuthInterceptor monitorAuthInterceptor() {
        return new MonitorAuthInterceptor();
    }
}
```

---

## 📝 总结

### Druid 监控页面的核心特点：

1. **统一 Servlet 处理**：StatViewServlet 既处理静态资源，也处理数据接口（通过 URL 后缀区分）
2. **不是 RESTful Controller**：不使用 Spring MVC 的 `@Controller`，而是直接用 `HttpServlet`
3. **直接读取数据**：数据接口直接从 DruidDataSource 内存中读取，不需要 Service 层
4. **静态资源内嵌**：HTML/CSS/JS 打包在 jar 的 `META-INF/resources/` 目录
5. **简单认证**：使用 Session 或 Basic Auth，用户名密码写死在配置中
6. **数据收集**：通过 Filter 收集数据，存储在内存中

### 关键区别：

| 方式 | Druid 实现 | 传统 Spring MVC |
|------|-----------|----------------|
| **请求处理** | 一个 Servlet 统一处理 | Controller 处理 API，静态资源分离 |
| **数据获取** | 直接从数据源读取 | 通过 Service 层获取 |
| **代码结构** | 简单，集中在 Servlet | 分层：Controller → Service → Repository |
| **适用场景** | 监控页面，功能简单 | 业务系统，功能复杂 |

### 对 Securt-Kit 的启发：

#### 方案 A：类似 Druid（推荐）
1. ✅ **统一 Controller 处理**：一个 Controller 既处理静态资源路由，也处理 JSON 数据接口
2. ✅ **直接读取数据**：从 TableCache、SqlParseCache 等直接读取，不需要 Service 层
3. ✅ **URL 后缀区分**：`.json` 结尾 = 数据接口，其他 = 静态资源或页面
4. ✅ **页面内嵌**：HTML/CSS/JS 放在 `resources/static/monitor/`，Spring Boot 自动处理
5. ✅ **简单认证**：使用 Session 认证，用户名密码写死在配置中

#### 方案 B：Spring Boot 标准方式
1. ✅ 静态资源：放在 `resources/static/monitor/`，Spring Boot 自动处理
2. ✅ API 接口：使用 `@RestController` 提供 JSON 接口
3. ✅ 数据获取：可以直接从内存读取，也可以加 Service 层（如果逻辑复杂）
4. ✅ 认证：通过拦截器统一保护 API 接口

**推荐方案 A**：更接近 Druid 的实现方式，代码更简洁，适合监控页面这种功能简单的场景。

---

**分析完成时间**：2025-01-XX  
**参考版本**：Druid 1.2.x

