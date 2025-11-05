# Servlet vs Controller 方案对比分析

> 针对 Securt-Kit 监控页面实现，对比 Servlet 和 Spring MVC Controller 两种方案的优缺点

---

## 📋 目录

1. [为什么 Druid 使用 Servlet](#为什么-druid-使用-servlet)
2. [两种方案对比](#两种方案对比)
3. [项目实际情况分析](#项目实际情况分析)
4. [推荐方案](#推荐方案)
5. [实现建议](#实现建议)

---

## 🔍 为什么 Druid 使用 Servlet

### 1.1 Druid 的设计目标

Druid 作为一个**通用数据库连接池**，需要：
- ✅ **兼容各种框架**：Spring Boot、Spring MVC、纯 Servlet、甚至非 Web 环境
- ✅ **零依赖**：最小化依赖，不强制依赖 Spring MVC
- ✅ **通用性**：可以在任何 Java Web 应用中运行

### 1.2 Servlet 的优势（对 Druid 而言）

```java
// Druid 的实现方式
public class StatViewServlet extends ResourceServlet {
    // 只依赖 Servlet API，不依赖 Spring
    // 可以在任何 Web 容器中运行
}
```

**优势**：
1. **最低依赖**：只依赖 Servlet API（J2EE 标准）
2. **通用性强**：适用于所有 Java Web 应用
3. **零框架依赖**：不依赖 Spring MVC，可以在非 Spring 环境中使用
4. **简单直接**：直接处理 HTTP 请求，无需额外的路由配置

---

## ⚖️ 两种方案对比

### 2.1 Servlet 方案

#### 实现方式

```java
public class MonitorServlet extends HttpServlet {
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        String uri = req.getRequestURI();
        // 手动处理路由、认证、静态资源
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        // 手动解析 JSON、处理业务逻辑
    }
}

// 注册方式（Spring Boot）
@Bean
public ServletRegistrationBean<MonitorServlet> monitorServlet(
        MonitorProperties properties) {
    MonitorServlet servlet = new MonitorServlet();
    servlet.setProperties(properties);
    return new ServletRegistrationBean<>(servlet, "/monitor/*");
}
```

#### ✅ 优点

| 优点 | 说明 |
|------|------|
| **最低依赖** | 只依赖 Servlet API（J2EE 标准，所有 Web 容器都支持） |
| **通用性强** | 可以在任何 Java Web 应用中运行（Spring、非 Spring、甚至纯 Servlet） |
| **零框架依赖** | 不依赖 Spring MVC，真正做到了低耦合 |
| **内存占用小** | 不需要 Spring MVC 的整个请求处理链 |
| **性能略好** | 直接处理请求，没有额外的抽象层 |
| **符合 Druid 模式** | 与 Druid 的设计理念一致，便于用户理解 |

#### ❌ 缺点

| 缺点 | 说明 |
|------|------|
| **代码量大** | 需要手动处理路由、参数解析、JSON 序列化等 |
| **功能重复** | Spring MVC 已经提供了这些功能，Servlet 方案需要重新实现 |
| **维护成本高** | 需要自己处理异常、参数验证、响应格式化等 |
| **静态资源处理复杂** | 需要手动读取类路径资源并输出 |
| **与 Spring Boot 集成不自然** | 虽然可以注册，但不如 Controller 方式集成顺畅 |
| **缺少 Spring 特性** | 无法使用 `@Autowired`、`@Value` 等 Spring 特性（需要手动获取 Bean） |

### 2.2 Controller 方案

#### 实现方式

```java
@RestController
@RequestMapping("/monitor")
public class MonitorController {
    
    @Autowired
    private MonitorProperties properties;
    
    @GetMapping("/**")
    public void handleGet(HttpServletRequest request, HttpServletResponse response) {
        // 处理静态资源或页面
    }
    
    @PostMapping("/api/encrypt.json")
    public ApiResponse encrypt(@RequestBody EncryptRequest request) {
        // Spring 自动处理 JSON 解析
        // 直接使用 @Autowired 注入依赖
    }
}
```

#### ✅ 优点

| 优点 | 说明 |
|------|------|
| **开发效率高** | Spring MVC 提供了丰富的功能，代码简洁 |
| **功能强大** | 自动处理 JSON 解析、参数绑定、异常处理等 |
| **Spring 集成好** | 可以无缝使用 `@Autowired`、`@Value`、`@ConfigurationProperties` 等 |
| **静态资源处理简单** | Spring Boot 自动处理静态资源，只需放在 `resources/static/` |
| **代码清晰** | 路由、参数、返回值都有明确的注解，代码可读性好 |
| **易于测试** | Spring 提供了完善的测试支持（MockMvc） |
| **统一异常处理** | 可以使用 `@ControllerAdvice` 统一处理异常 |
| **参数验证** | 可以使用 `@Valid`、`@NotNull` 等注解进行参数验证 |

#### ❌ 缺点

| 缺点 | 说明 |
|------|------|
| **依赖 Spring MVC** | 必须依赖 Spring Boot Web（实际上项目已经依赖了） |
| **仅适用于 Spring Boot** | 非 Spring Boot 环境无法使用（但项目本身就是 Spring Boot 项目） |
| **运行时依赖** | 需要 Spring MVC 的运行时库（但这已经包含在项目中） |

---

## 🔎 项目实际情况分析

### 3.1 项目技术栈

```
项目依赖情况：
├── securt-kit-core（核心模块，无 Spring 依赖）
├── securt-kit-starter（Spring Boot 自动配置）
│   └── 依赖 spring-boot-autoconfigure（provided scope）
├── securt-kit-ui（监控页面模块）
└── 使用场景：主要是 Spring Boot 应用
```

### 3.2 关键发现

1. **项目已经依赖 Spring Boot**
   - `securt-kit-starter` 依赖 `spring-boot-autoconfigure`
   - 测试项目使用 `@SpringBootApplication`
   - 项目主要面向 Spring Boot 用户

2. **securt-kit-ui 模块的定位**
   - 这是一个**可选模块**，用户可以选择是否引入
   - 如果使用监控页面，用户肯定已经引入了 Spring Boot Web
   - 用户大概率会使用 Spring Boot 环境

3. **低耦合的真正含义**
   - **核心模块（core）低耦合**：不依赖 Spring ✅（已实现）
   - **监控页面（ui）可以依赖 Spring**：因为它是可选功能 ✅（合理）

### 3.3 对比分析表

| 维度 | Servlet 方案 | Controller 方案 | 项目实际情况 |
|------|-------------|----------------|------------|
| **依赖 Spring MVC** | ❌ 不需要 | ✅ 需要 | ✅ 项目已依赖 Spring Boot |
| **适用于非 Spring 环境** | ✅ 可以 | ❌ 不可以 | ❓ 用户大概率使用 Spring Boot |
| **代码量** | ❌ 多（需手动处理很多） | ✅ 少（Spring 自动处理） | ✅ Controller 更简洁 |
| **开发效率** | ❌ 低 | ✅ 高 | ✅ Controller 更高效 |
| **维护成本** | ❌ 高 | ✅ 低 | ✅ Controller 更易维护 |
| **与现有代码集成** | ⚠️ 需要手动注册 | ✅ 自动配置 | ✅ Controller 集成更自然 |
| **功能完整性** | ⚠️ 需要自己实现 | ✅ Spring 提供 | ✅ Controller 功能更全 |

---

## 💡 推荐方案

### 4.1 推荐：使用 Controller 方案

**理由**：

1. **项目已经依赖 Spring Boot**
   - 既然项目主要面向 Spring Boot 用户，使用 Controller 方案更自然
   - 不需要为了通用性而牺牲开发效率和代码质量

2. **符合"低耦合"原则**
   - **核心模块**（core）保持低耦合 ✅
   - **监控页面**（ui）作为可选功能，可以依赖 Spring ✅
   - 如果用户不使用监控页面，可以选择不引入 `securt-kit-ui` 模块

3. **开发效率和维护成本**
   - Controller 方案代码量少，开发效率高
   - Spring 提供了完善的异常处理、参数验证等功能
   - 代码更清晰，易于维护

4. **实际使用场景**
   - 99% 的用户使用 Spring Boot 环境
   - 如果真的需要在非 Spring 环境使用，可以单独提供一个 Servlet 版本

### 4.2 特殊情况：如果需要支持非 Spring 环境

如果确实需要支持非 Spring 环境，可以：

**方案 A：提供两个版本**
```
securt-kit-ui/
├── monitor-servlet/      # Servlet 版本（可选）
└── monitor-spring/       # Spring Controller 版本（默认）
```

**方案 B：条件编译**
```java
// 自动检测环境，选择使用 Servlet 还是 Controller
@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
public class MonitorController { ... }

@ConditionalOnMissingClass("org.springframework.web.servlet.DispatcherServlet")
public class MonitorServlet extends HttpServlet { ... }
```

**但这样的复杂度不值得**，因为：
- 增加了代码复杂度
- 测试和维护成本增加
- 实际使用场景很少

---

## 🛠️ 实现建议

### 5.1 推荐实现方式（Controller）

```java
@RestController
@RequestMapping("${securtkit.monitor.path:/monitor}")
@ConditionalOnProperty(prefix = "securtkit.monitor", name = "enabled", havingValue = "true")
public class MonitorController {
    
    @Autowired
    private MonitorProperties properties;
    
    // 处理静态资源
    @GetMapping(value = {"/", "/index.html"})
    public void index(HttpServletRequest request, HttpServletResponse response) {
        // 检查登录，返回页面
    }
    
    // 处理加密请求
    @PostMapping("/api/encrypt.json")
    public ApiResponse<EncryptResponse> encrypt(
            @RequestBody EncryptRequest request,
            HttpSession session) {
        // 检查登录
        // 调用加密逻辑
        // 返回结果
    }
    
    // 处理解密请求
    @PostMapping("/api/decrypt.json")
    public ApiResponse<DecryptResponse> decrypt(
            @RequestBody DecryptRequest request,
            HttpSession session) {
        // 检查登录
        // 调用解密逻辑
        // 返回结果
    }
}
```

### 5.2 如果坚持使用 Servlet（参考 Druid）

```java
public class MonitorServlet extends HttpServlet {
    
    private MonitorProperties properties;
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        // 手动处理路由、认证、静态资源
        String uri = req.getRequestURI();
        if (uri.endsWith(".json")) {
            // 处理 JSON 接口
        } else {
            // 处理静态资源
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        // 手动解析 JSON
        // 手动处理业务逻辑
        // 手动序列化响应
    }
}
```

---

## 📊 最终对比总结

### 6.1 关键差异

| 特性 | Servlet | Controller |
|------|---------|-----------|
| **依赖** | 仅 Servlet API | Spring MVC |
| **代码量** | ~500 行 | ~200 行 |
| **路由处理** | 手动解析 URI | `@RequestMapping` |
| **参数解析** | 手动解析 JSON | `@RequestBody` 自动处理 |
| **静态资源** | 手动读取并输出 | Spring Boot 自动处理 |
| **异常处理** | 手动 try-catch | `@ControllerAdvice` |
| **参数验证** | 手动验证 | `@Valid` + `@NotNull` |
| **Spring 集成** | 需要手动获取 Bean | `@Autowired` 自动注入 |

### 6.2 选择建议

**使用 Controller 方案，如果**：
- ✅ 项目主要面向 Spring Boot 用户（**你的项目**）
- ✅ 希望代码简洁、易维护
- ✅ 需要快速开发
- ✅ 监控页面是可选功能

**使用 Servlet 方案，如果**：
- ✅ 必须支持非 Spring 环境
- ✅ 需要最小的依赖
- ✅ 希望与 Druid 的实现方式完全一致

---

## 🎯 最终推荐

**推荐使用 Controller 方案**，理由：

1. ✅ **项目已依赖 Spring Boot**：不需要为了通用性牺牲开发效率
2. ✅ **符合"低耦合"原则**：核心模块保持低耦合，监控页面作为可选功能可以依赖 Spring
3. ✅ **开发效率高**：代码量少，功能完整，易于维护
4. ✅ **实际使用场景**：99% 的用户使用 Spring Boot 环境

**如果未来真的需要支持非 Spring 环境**，可以：
- 单独提供一个 Servlet 版本的模块
- 或者通过条件编译实现

但现在**不需要**为了小概率场景而增加复杂度。

---

**结论**：对于你的项目，**Controller 方案更合适**。

