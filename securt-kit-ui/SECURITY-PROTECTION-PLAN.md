# 监控页面安全防护方案

## 📋 目录
1. [安全威胁分析](#安全威胁分析)
2. [防护方案](#防护方案)
3. [实施计划](#实施计划)
4. [技术实现](#技术实现)

---

## 🔍 安全威胁分析

### 1.1 用户输入点识别

| 输入点 | 类型 | 潜在威胁 |
|--------|------|----------|
| 登录用户名/密码 | 表单输入 | XSS、暴力破解 |
| 加密解密内容 | 文本输入 | XSS、注入攻击 |
| 表名/字段名 | 文本输入 | SQL注入、XSS |
| 策略类名 | 文本输入 | 代码注入、反射攻击 |
| SQL语句 | 文本输入 | SQL注入、XSS |
| 分页参数 | 数字输入 | SQL注入、整数溢出 |

### 1.2 主要安全风险

#### 1. XSS（跨站脚本攻击）
- **存储型XSS**：恶意脚本存储在数据库中，每次访问时执行
- **反射型XSS**：恶意脚本通过URL参数注入，立即执行
- **DOM型XSS**：通过修改DOM结构执行恶意脚本

#### 2. SQL注入
- **SQL查询功能**：虽然使用PreparedStatement，但仍需验证SQL格式
- **SQL解析功能**：直接解析用户输入的SQL，存在注入风险
- **SQL参数加密**：需要验证SQL语法正确性

#### 3. CSRF（跨站请求伪造）
- 未登录用户可能被诱导执行恶意操作
- 已登录用户的会话可能被利用

#### 4. 其他风险
- **暴力破解**：登录接口缺少速率限制
- **信息泄露**：错误信息可能泄露敏感信息
- **代码注入**：策略类名可能被利用进行反射攻击

---

## 🛡️ 防护方案

### 2.1 XSS 防护

#### 前端防护
1. **输出编码**
   - 所有用户输入在显示前进行HTML转义
   - 使用 `textContent` 而非 `innerHTML`
   - 如需HTML，使用DOMPurify等库进行清理

2. **CSP（内容安全策略）**
   - 设置严格的CSP头，禁止内联脚本
   - 只允许同源资源加载

3. **输入验证**
   - 前端实时验证输入格式
   - 限制特殊字符输入

#### 后端防护
1. **输入验证和清理**
   - 使用 `@Valid` 注解验证输入
   - 自定义验证器过滤危险字符
   - 对特殊字符进行转义

2. **输出编码**
   - 所有响应数据在返回前进行编码
   - JSON响应自动转义特殊字符

3. **HTTP安全头**
   - `X-Content-Type-Options: nosniff`
   - `X-Frame-Options: DENY`
   - `X-XSS-Protection: 1; mode=block`

### 2.2 SQL注入防护

#### 1. SQL查询功能
- ✅ **已实现**：使用 `PreparedStatement` 参数化查询
- ✅ **已实现**：只支持SELECT语句
- 🔄 **需加强**：
  - 验证SQL语法正确性
  - 限制SQL长度（如最大10KB）
  - 禁止危险关键字（DROP、DELETE、TRUNCATE等）
  - 限制查询结果数量

#### 2. SQL解析功能
- 🔄 **需实现**：
  - 验证SQL格式（只允许SELECT、INSERT、UPDATE、DELETE）
  - 限制SQL长度
  - 使用白名单验证SQL关键字
  - 记录解析日志

#### 3. SQL参数加密功能
- 🔄 **需实现**：
  - 验证SQL语法正确性
  - 限制SQL长度
  - 禁止危险操作（DROP、ALTER等）

### 2.3 CSRF防护

#### 1. CSRF Token
- 在Session中生成CSRF Token
- 所有POST请求必须携带Token
- 前端从后端获取Token并附加到请求头

#### 2. SameSite Cookie
- 设置Cookie的SameSite属性为Strict

#### 3. Referer验证
- 验证请求来源（可选，可能影响用户体验）

### 2.4 输入验证和过滤

#### 1. 长度限制
| 输入项 | 最大长度 | 说明 |
|--------|----------|------|
| 用户名 | 50字符 | 登录用户名 |
| 密码 | 100字符 | 登录密码 |
| 加密内容 | 10KB | 加密/解密文本 |
| 表名 | 64字符 | 数据库表名 |
| 字段名 | 64字符 | 数据库字段名 |
| 策略类名 | 200字符 | 完整类名 |
| SQL语句 | 50KB | SQL查询语句 |

#### 2. 格式验证
- **表名/字段名**：只允许字母、数字、下划线
- **策略类名**：Java类名格式验证
- **SQL语句**：SQL语法验证（使用JSQLParser）
- **分页参数**：正整数，范围限制

#### 3. 特殊字符过滤
- 过滤HTML标签：`<script>`, `<iframe>`, `<object>`等
- 过滤SQL关键字：`DROP`, `TRUNCATE`, `ALTER`等（在非查询场景）
- 过滤JavaScript事件：`onclick`, `onerror`等

### 2.5 速率限制

#### 1. 登录接口
- 每个IP最多5次/分钟
- 失败后锁定30分钟

#### 2. API接口
- 每个用户最多100次/分钟
- 超过限制返回429状态码

#### 3. 实现方式
- 使用Spring的`@RateLimiter`注解
- 或使用Redis实现分布式限流

### 2.6 其他安全措施

#### 1. 日志记录
- 记录所有登录尝试（成功/失败）
- 记录所有SQL查询操作
- 记录异常操作（如SQL注入尝试）

#### 2. 错误处理
- 不泄露敏感信息（数据库结构、错误堆栈等）
- 统一错误响应格式
- 生产环境隐藏详细错误信息

#### 3. 会话管理
- 会话超时：30分钟无操作自动退出
- 会话固定防护：登录后重新生成Session ID
- 单点登录限制：同一用户只能有一个活跃会话

#### 4. 访问控制
- IP白名单（可选）
- 只允许HTTPS访问（生产环境）
- 限制访问时间（可选）

---

## 📅 实施计划

### 阶段一：基础防护（优先级：高）
1. ✅ XSS防护：前端输出编码
2. ✅ SQL注入防护：SQL查询功能加强验证
3. ✅ 输入验证：长度限制和格式验证
4. ✅ HTTP安全头：添加安全响应头

### 阶段二：高级防护（优先级：中）
1. 🔄 CSRF防护：实现CSRF Token
2. 🔄 速率限制：实现登录和API限流
3. 🔄 会话管理：会话超时和固定防护
4. 🔄 日志记录：安全事件日志

### 阶段三：增强防护（优先级：低）
1. ⏳ CSP策略：内容安全策略
2. ⏳ IP白名单：访问控制
3. ⏳ 审计日志：详细操作审计

---

## 🔧 技术实现

### 3.1 前端实现

#### HTML转义工具函数
```javascript
// 转义HTML特殊字符
function escapeHtml(text) {
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return text.replace(/[&<>"']/g, m => map[m]);
}

// 安全地设置文本内容
function setTextContent(element, text) {
    if (element) {
        element.textContent = text; // 自动转义
    }
}
```

#### 输入验证
```javascript
// 验证表名/字段名
function validateTableOrFieldName(name) {
    return /^[a-zA-Z_][a-zA-Z0-9_]*$/.test(name);
}

// 验证SQL长度
function validateSqlLength(sql) {
    return sql.length <= 50 * 1024; // 50KB
}
```

### 3.2 后端实现

#### 输入验证注解
```java
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SafeInputValidator.class)
public @interface SafeInput {
    int maxLength() default 1000;
    boolean allowHtml() default false;
    String message() default "输入包含不安全字符";
}
```

#### SQL验证工具
```java
public class SqlValidator {
    private static final Set<String> DANGEROUS_KEYWORDS = Set.of(
        "DROP", "TRUNCATE", "ALTER", "CREATE", "GRANT", "REVOKE"
    );
    
    public static boolean isValidSql(String sql, boolean allowDML) {
        // 验证SQL长度
        if (sql.length() > 50 * 1024) {
            return false;
        }
        
        // 验证SQL语法
        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            // 验证语句类型
            if (!allowDML && !(stmt instanceof Select)) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

#### CSRF Token生成
```java
@PostMapping("/api/csrf-token.json")
public ApiResponse<String> getCsrfToken(HttpSession session) {
    String token = UUID.randomUUID().toString();
    session.setAttribute("csrf_token", token);
    return ApiResponse.success(token);
}
```

#### 速率限制拦截器
```java
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) {
        String key = getClientKey(request);
        AtomicInteger count = requestCounts.computeIfAbsent(key, 
            k -> new AtomicInteger(0));
        
        if (count.incrementAndGet() > 100) {
            response.setStatus(429);
            return false;
        }
        
        // 每分钟重置计数
        scheduleReset(key);
        return true;
    }
}
```

---

## 📊 安全等级评估

| 安全措施 | 实施难度 | 防护效果 | 优先级 |
|---------|---------|---------|--------|
| XSS防护（输出编码） | 低 | 高 | ⭐⭐⭐ |
| SQL注入防护（验证） | 中 | 高 | ⭐⭐⭐ |
| 输入验证（长度/格式） | 低 | 中 | ⭐⭐⭐ |
| CSRF防护（Token） | 中 | 高 | ⭐⭐ |
| 速率限制 | 中 | 中 | ⭐⭐ |
| HTTP安全头 | 低 | 中 | ⭐⭐ |
| 会话管理 | 低 | 中 | ⭐ |
| CSP策略 | 中 | 高 | ⭐ |
| 日志记录 | 低 | 低 | ⭐ |

---

## ✅ 检查清单

### 前端安全检查
- [ ] 所有用户输入在显示前进行HTML转义
- [ ] 使用 `textContent` 而非 `innerHTML`
- [ ] 实现输入长度和格式验证
- [ ] 添加CSP策略
- [ ] 实现CSRF Token获取和发送

### 后端安全检查
- [ ] 所有输入参数进行验证和清理
- [ ] SQL查询使用PreparedStatement
- [ ] SQL语句进行语法验证和关键字过滤
- [ ] 实现速率限制
- [ ] 添加HTTP安全响应头
- [ ] 实现CSRF Token验证
- [ ] 记录安全事件日志
- [ ] 统一错误处理，不泄露敏感信息

---

## 📝 注意事项

1. **性能影响**：安全措施可能影响性能，需要平衡安全性和性能
2. **用户体验**：过于严格的安全措施可能影响用户体验
3. **维护成本**：安全措施需要持续维护和更新
4. **测试覆盖**：需要编写安全测试用例，确保防护措施有效

---

**文档版本**：v1.0  
**创建日期**：2025-01-XX  
**最后更新**：2025-01-XX

