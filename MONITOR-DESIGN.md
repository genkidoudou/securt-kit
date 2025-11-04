# Securt-Kit 监控管理页面设计方案（简化版）

> 参考 Druid 监控页面实现方式，页面内嵌到项目中，简单实用

---

## 📋 设计原则

1. **简单实用**：页面直接内嵌在项目中，无需前后端分离
2. **开箱即用**：配置简单，用户名密码写死在配置文件中
3. **轻量级**：使用原生 HTML/CSS/JS，无需构建工具
4. **参考 Druid**：采用类似的实现方式

---

## 🏗️ 整体架构

```
┌─────────────────────────────────────────────┐
│         Securt-Kit 项目（Spring Boot）       │
├─────────────────────────────────────────────┤
│                                             │
│  ┌─────────────────────────────────────┐  │
│  │  resources/static/monitor/          │  │
│  │    ├── index.html  (主页面)           │  │
│  │    ├── css/style.css                 │  │
│  │    └── js/app.js                      │  │
│  └─────────────────────────────────────┘  │
│              │                             │
│              ▼                             │
│  ┌─────────────────────────────────────┐  │
│  │  MonitorController (REST API)       │  │
│  │    ├── /monitor/login               │  │
│  │    ├── /monitor/api/encrypt          │  │
│  │    ├── /monitor/api/decrypt          │  │
│  │    ├── /monitor/api/sql/convert     │  │
│  │    └── /monitor/api/query/execute   │  │
│  └─────────────────────────────────────┘  │
│              │                             │
│              ▼                             │
│  ┌─────────────────────────────────────┐  │
│  │  Securt-Kit Core                     │  │
│  │    ├── SecurtkitUtils                │  │
│  │    ├── TableCache                    │  │
│  │    └── StrategyCache                 │  │
│  └─────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

---

## 🛠️ 技术实现

### 1. 模块结构

不需要新建独立模块，直接在 `securt-kit-core` 或 `securt-kit-starter` 中添加：

```
securt-kit-core/
├── src/main/java/
│   └── io/github/hexlodev/core/
│       └── monitor/
│           ├── MonitorController.java      # 监控页面 Controller
│           ├── MonitorService.java          # 业务逻辑
│           └── MonitorConfig.java           # 配置类
└── src/main/resources/
    └── static/
        └── monitor/
            ├── index.html                   # 主页面
            ├── css/
            │   └── style.css
            └── js/
                └── app.js
```

### 2. 认证方式

**简单 Session 认证**（参考 Druid）

```java
@Controller
@RequestMapping("/monitor")
public class MonitorController {
    
    @PostMapping("/login")
    public String login(@RequestParam String username, 
                       @RequestParam String password,
                       HttpSession session) {
        // 从配置文件读取用户名密码
        String configUsername = properties.getMonitorUsername();
        String configPassword = properties.getMonitorPassword();
        
        if (configUsername.equals(username) && 
            configPassword.equals(password)) {
            session.setAttribute("monitor_logged_in", true);
            return "redirect:/monitor/index.html";
        }
        return "redirect:/monitor/index.html?error=1";
    }
    
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/monitor/index.html";
    }
}
```

**页面访问拦截**：
```java
@ControllerAdvice
public class MonitorInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) {
        // 只拦截 /monitor/api/* 路径
        if (request.getRequestURI().startsWith("/monitor/api/")) {
            HttpSession session = request.getSession(false);
            if (session == null || 
                session.getAttribute("monitor_logged_in") == null) {
                response.setStatus(401);
                return false;
            }
        }
        return true;
    }
}
```

---

## ⚙️ 配置设计

### 配置文件

```yaml
securtkit:
  encryptor:
    # ... 原有配置
    
  monitor:
    enabled: true                    # 是否启用监控页面
    username: admin                  # 用户名（写死）
    password: admin123               # 密码（写死）
    path: /monitor                   # 访问路径（默认 /monitor）
```

### 配置类

```java
@ConfigurationProperties(prefix = "securtkit.monitor")
@Data
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
     * 监控页面访问路径
     */
    private String path = "/monitor";
}
```

---

## 📄 页面设计

### 1. 页面结构（单页应用）

**index.html** - 主页面，包含所有功能

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Securt-Kit 监控管理</title>
    <link rel="stylesheet" href="/monitor/css/style.css">
</head>
<body>
    <!-- 登录页面（初始显示） -->
    <div id="loginPage" class="page">
        <div class="login-box">
            <h2>Securt-Kit 监控管理</h2>
            <form id="loginForm" onsubmit="handleLogin(event)">
                <div class="form-group">
                    <label>用户名：</label>
                    <input type="text" id="username" required>
                </div>
                <div class="form-group">
                    <label>密码：</label>
                    <input type="password" id="password" required>
                </div>
                <div id="loginError" class="error-message"></div>
                <button type="submit">登录</button>
            </form>
        </div>
    </div>

    <!-- 主功能页面（登录后显示） -->
    <div id="mainPage" class="page" style="display:none;">
        <header>
            <h1>Securt-Kit 监控管理</h1>
            <button onclick="logout()">退出</button>
        </header>
        
        <nav>
            <button class="tab-btn active" onclick="switchTab('encrypt')">加密解密</button>
            <button class="tab-btn" onclick="switchTab('sql')">SQL转换</button>
            <button class="tab-btn" onclick="switchTab('query')">查询解密</button>
            <button class="tab-btn" onclick="switchTab('config')">配置管理</button>
        </nav>

        <!-- Tab 1: 加密解密工具 -->
        <div id="encryptTab" class="tab-content active">
            <h2>字符串加密/解密工具</h2>
            <div class="form-group">
                <label>加密策略：</label>
                <select id="encryptStrategy">
                    <option value="">使用默认策略</option>
                </select>
            </div>
            <div class="form-group">
                <label>表名：</label>
                <input type="text" id="encryptTable" placeholder="如: user">
            </div>
            <div class="form-group">
                <label>字段名：</label>
                <input type="text" id="encryptField" placeholder="如: name">
            </div>
            <div class="form-group">
                <label>明文：</label>
                <textarea id="encryptText" rows="5" placeholder="输入要加密的文本"></textarea>
            </div>
            <button onclick="doEncrypt()">加密</button>
            <button onclick="doDecrypt()">解密</button>
            <div class="form-group">
                <label>结果：</label>
                <textarea id="encryptResult" rows="5" readonly></textarea>
                <button onclick="copyResult('encryptResult')">复制</button>
            </div>
        </div>

        <!-- Tab 2: SQL 转换 -->
        <div id="sqlTab" class="tab-content">
            <h2>SQL 明文转加密 SQL</h2>
            <div class="form-group">
                <label>原始 SQL：</label>
                <textarea id="sqlOriginal" rows="8" 
                    placeholder="UPDATE user SET name = ?, phone = ? WHERE id = ?"></textarea>
            </div>
            <div class="form-group">
                <label>参数值（JSON 格式，可选）：</label>
                <textarea id="sqlParams" rows="5" 
                    placeholder='{"1": "张三", "2": "13800138000", "3": "1"}'></textarea>
            </div>
            <button onclick="convertSql()">转换</button>
            <div class="form-group">
                <label>转换结果：</label>
                <textarea id="sqlResult" rows="8" readonly></textarea>
                <button onclick="copyResult('sqlResult')">复制</button>
            </div>
        </div>

        <!-- Tab 3: 查询解密 -->
        <div id="queryTab" class="tab-content">
            <h2>查询结果自动解密</h2>
            <div class="form-group">
                <label>查询 SQL：</label>
                <textarea id="querySql" rows="5" 
                    placeholder="SELECT * FROM user WHERE id = ?"></textarea>
            </div>
            <div class="form-group">
                <label>参数值（JSON 格式，可选）：</label>
                <textarea id="queryParams" rows="3" placeholder='["1"]'></textarea>
            </div>
            <button onclick="executeQuery()">执行查询</button>
            <div id="queryResult" class="result-table"></div>
        </div>

        <!-- Tab 4: 配置管理 -->
        <div id="configTab" class="tab-content">
            <h2>配置管理</h2>
            <div class="config-section">
                <h3>加密表配置</h3>
                <div id="configTables"></div>
            </div>
            <div class="config-section">
                <h3>缓存状态</h3>
                <div id="configCache"></div>
                <button onclick="refreshCache()">刷新缓存</button>
                <button onclick="clearCache()">清空缓存</button>
            </div>
        </div>
    </div>

    <script src="/monitor/js/app.js"></script>
</body>
</html>
```

### 2. 样式设计（style.css）

```css
/* 参考 Druid 监控页面的简洁风格 */
body {
    font-family: Arial, sans-serif;
    margin: 0;
    padding: 0;
    background-color: #f5f5f5;
}

/* 登录页面 */
.login-box {
    width: 400px;
    margin: 100px auto;
    padding: 30px;
    background: white;
    border-radius: 5px;
    box-shadow: 0 2px 10px rgba(0,0,0,0.1);
}

/* 主页面 */
header {
    background: #2c3e50;
    color: white;
    padding: 15px 20px;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

nav {
    background: #34495e;
    padding: 10px 20px;
}

.tab-btn {
    background: #34495e;
    color: white;
    border: none;
    padding: 10px 20px;
    cursor: pointer;
    margin-right: 5px;
}

.tab-btn.active {
    background: #3498db;
}

.tab-content {
    padding: 20px;
    display: none;
}

.tab-content.active {
    display: block;
}

.form-group {
    margin-bottom: 15px;
}

.form-group label {
    display: block;
    margin-bottom: 5px;
    font-weight: bold;
}

.form-group input,
.form-group textarea,
.form-group select {
    width: 100%;
    padding: 8px;
    border: 1px solid #ddd;
    border-radius: 3px;
    box-sizing: border-box;
}

button {
    background: #3498db;
    color: white;
    border: none;
    padding: 10px 20px;
    cursor: pointer;
    border-radius: 3px;
    margin-right: 10px;
}

button:hover {
    background: #2980b9;
}

.error-message {
    color: red;
    margin-top: 10px;
}

.result-table {
    margin-top: 20px;
    overflow-x: auto;
}

table {
    width: 100%;
    border-collapse: collapse;
    background: white;
}

table th,
table td {
    border: 1px solid #ddd;
    padding: 8px;
    text-align: left;
}

table th {
    background: #f2f2f2;
}
```

### 3. JavaScript（app.js）

```javascript
// 检查登录状态
function checkLogin() {
    // 页面加载时检查是否已登录
    fetch('/monitor/api/check')
        .then(res => {
            if (res.ok) {
                document.getElementById('loginPage').style.display = 'none';
                document.getElementById('mainPage').style.display = 'block';
                loadConfig();
            }
        })
        .catch(() => {
            // 未登录，显示登录页面
        });
}

// 登录
function handleLogin(event) {
    event.preventDefault();
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;
    
    const formData = new FormData();
    formData.append('username', username);
    formData.append('password', password);
    
    fetch('/monitor/login', {
        method: 'POST',
        body: formData
    })
    .then(res => {
        if (res.redirected) {
            window.location.href = res.url;
        } else {
            document.getElementById('loginError').textContent = '用户名或密码错误';
        }
    });
}

// 退出
function logout() {
    fetch('/monitor/logout')
        .then(() => {
            window.location.href = '/monitor/index.html';
        });
}

// Tab 切换
function switchTab(tabName) {
    // 隐藏所有 tab
    document.querySelectorAll('.tab-content').forEach(tab => {
        tab.classList.remove('active');
    });
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.remove('active');
    });
    
    // 显示选中的 tab
    document.getElementById(tabName + 'Tab').classList.add('active');
    event.target.classList.add('active');
}

// 加密
function doEncrypt() {
    const text = document.getElementById('encryptText').value;
    const strategy = document.getElementById('encryptStrategy').value;
    const tableName = document.getElementById('encryptTable').value;
    const fieldName = document.getElementById('encryptField').value;
    
    fetch('/monitor/api/encrypt', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({text, strategy, tableName, fieldName})
    })
    .then(res => res.json())
    .then(data => {
        document.getElementById('encryptResult').value = data.encrypted || data.message;
    });
}

// 解密
function doDecrypt() {
    const text = document.getElementById('encryptText').value;
    const strategy = document.getElementById('encryptStrategy').value;
    const tableName = document.getElementById('encryptTable').value;
    const fieldName = document.getElementById('encryptField').value;
    
    fetch('/monitor/api/decrypt', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({text, strategy, tableName, fieldName})
    })
    .then(res => res.json())
    .then(data => {
        document.getElementById('encryptResult').value = data.decrypted || data.message;
    });
}

// SQL 转换
function convertSql() {
    const sql = document.getElementById('sqlOriginal').value;
    let params = {};
    try {
        const paramsText = document.getElementById('sqlParams').value;
        if (paramsText) {
            params = JSON.parse(paramsText);
        }
    } catch (e) {
        alert('参数格式错误，请输入有效的 JSON');
        return;
    }
    
    fetch('/monitor/api/sql/convert', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({sql, parameters: params})
    })
    .then(res => res.json())
    .then(data => {
        document.getElementById('sqlResult').value = data.convertedSql || data.message;
    });
}

// 执行查询
function executeQuery() {
    const sql = document.getElementById('querySql').value;
    let params = [];
    try {
        const paramsText = document.getElementById('queryParams').value;
        if (paramsText) {
            params = JSON.parse(paramsText);
        }
    } catch (e) {
        alert('参数格式错误，请输入有效的 JSON 数组');
        return;
    }
    
    fetch('/monitor/api/query/execute', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({sql, parameters: params})
    })
    .then(res => res.json())
    .then(data => {
        renderQueryResult(data);
    });
}

// 渲染查询结果
function renderQueryResult(data) {
    const container = document.getElementById('queryResult');
    if (!data.columns || !data.rows) {
        container.innerHTML = '<p>查询失败：' + (data.message || '未知错误') + '</p>';
        return;
    }
    
    let html = '<table><thead><tr>';
    data.columns.forEach(col => {
        html += '<th>' + col + '</th>';
    });
    html += '</tr></thead><tbody>';
    
    data.rows.forEach(row => {
        html += '<tr>';
        data.columns.forEach(col => {
            html += '<td>' + (row[col] || '') + '</td>';
        });
        html += '</tr>';
    });
    html += '</tbody></table>';
    
    container.innerHTML = html;
}

// 加载配置
function loadConfig() {
    fetch('/monitor/api/config')
        .then(res => res.json())
        .then(data => {
            renderConfig(data);
        });
}

// 渲染配置
function renderConfig(data) {
    // 渲染表配置
    let tablesHtml = '<ul>';
    if (data.tables) {
        data.tables.forEach(table => {
            tablesHtml += '<li>' + table.tableName + ': ' + table.fields.join(', ') + '</li>';
        });
    }
    tablesHtml += '</ul>';
    document.getElementById('configTables').innerHTML = tablesHtml;
    
    // 渲染缓存状态
    if (data.cache) {
        document.getElementById('configCache').innerHTML = 
            '<p>SQL 解析缓存: ' + data.cache.sqlParseCache + '</p>' +
            '<p>策略缓存: ' + data.cache.strategyCache + '</p>';
    }
}

// 复制结果
function copyResult(elementId) {
    const text = document.getElementById(elementId).value;
    navigator.clipboard.writeText(text).then(() => {
        alert('已复制到剪贴板');
    });
}

// 页面加载时检查登录状态
window.onload = checkLogin;
```

---

## 🔌 后端 API 设计

### Controller 实现

```java
@Controller
@RequestMapping("/monitor")
@Slf4j
public class MonitorController {

    @Autowired
    private MonitorService monitorService;
    
    @Autowired
    private MonitorProperties properties;

    /**
     * 登录页面
     */
    @GetMapping("/index.html")
    public String index() {
        return "forward:/static/monitor/index.html";
    }

    /**
     * 登录接口
     */
    @PostMapping("/login")
    public String login(@RequestParam String username, 
                       @RequestParam String password,
                       HttpSession session,
                       RedirectAttributes redirectAttributes) {
        if (properties.getUsername().equals(username) && 
            properties.getPassword().equals(password)) {
            session.setAttribute("monitor_logged_in", true);
            return "redirect:/monitor/index.html";
        }
        redirectAttributes.addFlashAttribute("error", "用户名或密码错误");
        return "redirect:/monitor/index.html";
    }

    /**
     * 退出登录
     */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/monitor/index.html";
    }

    /**
     * 检查登录状态
     */
    @GetMapping("/api/check")
    @ResponseBody
    public ResponseEntity<?> checkLogin(HttpSession session) {
        if (session.getAttribute("monitor_logged_in") != null) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(401).build();
    }

    /**
     * 加密接口
     */
    @PostMapping("/api/encrypt")
    @ResponseBody
    public ResponseEntity<?> encrypt(@RequestBody EncryptRequest request,
                                    HttpSession session) {
        if (!checkAuth(session)) {
            return ResponseEntity.status(401).build();
        }
        try {
            String encrypted = monitorService.encrypt(
                request.getText(), 
                request.getTableName(), 
                request.getFieldName(),
                request.getStrategy()
            );
            return ResponseEntity.ok(Map.of("encrypted", encrypted));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("message", "加密失败: " + e.getMessage()));
        }
    }

    /**
     * 解密接口
     */
    @PostMapping("/api/decrypt")
    @ResponseBody
    public ResponseEntity<?> decrypt(@RequestBody DecryptRequest request,
                                    HttpSession session) {
        if (!checkAuth(session)) {
            return ResponseEntity.status(401).build();
        }
        try {
            String decrypted = monitorService.decrypt(
                request.getText(),
                request.getTableName(),
                request.getFieldName(),
                request.getStrategy()
            );
            return ResponseEntity.ok(Map.of("decrypted", decrypted));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("message", "解密失败: " + e.getMessage()));
        }
    }

    /**
     * SQL 转换接口
     */
    @PostMapping("/api/sql/convert")
    @ResponseBody
    public ResponseEntity<?> convertSql(@RequestBody SqlConvertRequest request,
                                       HttpSession session) {
        if (!checkAuth(session)) {
            return ResponseEntity.status(401).build();
        }
        try {
            SqlConvertResult result = monitorService.convertSql(
                request.getSql(),
                request.getParameters()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("message", "转换失败: " + e.getMessage()));
        }
    }

    /**
     * 查询执行接口
     */
    @PostMapping("/api/query/execute")
    @ResponseBody
    public ResponseEntity<?> executeQuery(@RequestBody QueryRequest request,
                                         HttpSession session) {
        if (!checkAuth(session)) {
            return ResponseEntity.status(401).build();
        }
        try {
            QueryResult result = monitorService.executeQuery(
                request.getSql(),
                request.getParameters()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("message", "查询失败: " + e.getMessage()));
        }
    }

    /**
     * 获取配置信息
     */
    @GetMapping("/api/config")
    @ResponseBody
    public ResponseEntity<?> getConfig(HttpSession session) {
        if (!checkAuth(session)) {
            return ResponseEntity.status(401).build();
        }
        ConfigInfo config = monitorService.getConfigInfo();
        return ResponseEntity.ok(config);
    }

    /**
     * 检查认证
     */
    private boolean checkAuth(HttpSession session) {
        return session != null && 
               session.getAttribute("monitor_logged_in") != null;
    }
}
```

### Service 实现

```java
@Service
@Slf4j
public class MonitorService {

    @Autowired
    private DataSource dataSource; // 使用应用的主数据源

    /**
     * 加密字符串
     */
    public String encrypt(String text, String tableName, String fieldName, String strategy) {
        // 获取加密策略
        Class<? extends FieldEncryptorStrategy> strategyClass = null;
        if (StrUtil.isNotBlank(strategy)) {
            try {
                strategyClass = (Class<? extends FieldEncryptorStrategy>) Class.forName(strategy);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("策略类不存在: " + strategy);
            }
        } else {
            // 使用表配置的策略
            strategyClass = TableCache.getTableFieldEncryptInfo(tableName, fieldName);
        }
        
        if (strategyClass == null) {
            throw new RuntimeException("未找到加密策略");
        }
        
        FieldEncryptorStrategy encryptor = StrategyCache.getStrategy(strategyClass);
        return EncryptionHandler.handleEncryption(
            text, tableName, fieldName,
            () -> encryptor.encryption(text),
            null
        );
    }

    /**
     * 解密字符串
     */
    public String decrypt(String text, String tableName, String fieldName, String strategy) {
        // 类似加密逻辑
        Class<? extends FieldEncryptorStrategy> strategyClass = null;
        if (StrUtil.isNotBlank(strategy)) {
            try {
                strategyClass = (Class<? extends FieldEncryptorStrategy>) Class.forName(strategy);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("策略类不存在: " + strategy);
            }
        } else {
            strategyClass = TableCache.getTableFieldEncryptInfo(tableName, fieldName);
        }
        
        if (strategyClass == null) {
            throw new RuntimeException("未找到解密策略");
        }
        
        FieldEncryptorStrategy decryptor = StrategyCache.getStrategy(strategyClass);
        return EncryptionHandler.handleDecryption(
            text, tableName, fieldName,
            () -> decryptor.decryption(text),
            null
        );
    }

    /**
     * SQL 转换
     */
    public SqlConvertResult convertSql(String sql, Map<String, String> parameters) {
        // 1. 解析 SQL
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult = 
            SecurtkitUtils.parseSql(sql);
        
        // 2. 识别需要加密的字段
        Map<String, ColumnTableDto> placeholderMap = parseResult.getKey();
        
        // 3. 构建转换后的 SQL
        String convertedSql = sql;
        List<EncryptedFieldInfo> encryptedFields = new ArrayList<>();
        
        for (Map.Entry<String, ColumnTableDto> entry : placeholderMap.entrySet()) {
            String placeholder = entry.getKey();
            ColumnTableDto columnDto = entry.getValue();
            int paramIndex = columnDto.getInsertFieldIndex();
            
            String paramKey = String.valueOf(paramIndex);
            if (parameters != null && parameters.containsKey(paramKey)) {
                String originalValue = parameters.get(paramKey);
                
                // 加密值
                Class<? extends FieldEncryptorStrategy> strategyClass = 
                    TableCache.getTableFieldEncryptInfo(
                        columnDto.getSourceTableName(),
                        columnDto.getSourceColumn()
                    );
                
                if (strategyClass != null) {
                    FieldEncryptorStrategy strategy = StrategyCache.getStrategy(strategyClass);
                    String encryptedValue = strategy.encryption(originalValue);
                    
                    // 替换占位符（需要转义单引号）
                    String escapedValue = encryptedValue.replace("'", "''");
                    convertedSql = convertedSql.replaceFirst("\\?", "'" + escapedValue + "'");
                    
                    encryptedFields.add(new EncryptedFieldInfo(
                        paramIndex,
                        columnDto.getSourceColumn(),
                        columnDto.getSourceTableName(),
                        originalValue,
                        encryptedValue
                    ));
                } else {
                    // 不需要加密，直接替换
                    String escapedValue = originalValue.replace("'", "''");
                    convertedSql = convertedSql.replaceFirst("\\?", "'" + escapedValue + "'");
                }
            }
        }
        
        return new SqlConvertResult(sql, convertedSql, encryptedFields);
    }

    /**
     * 执行查询并自动解密
     */
    public QueryResult executeQuery(String sql, List<Object> parameters) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            // 设置参数
            if (parameters != null) {
                for (int i = 0; i < parameters.size(); i++) {
                    stmt.setObject(i + 1, parameters.get(i));
                }
            }
            
            // 执行查询
            ResultSet rs = stmt.executeQuery();
            
            // 解析 SQL 获取表信息
            Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult = 
                SecurtkitUtils.parseSql(sql);
            Set<String> tables = new HashSet<>();
            parseResult.getKey().values().forEach(dto -> 
                tables.add(dto.getSourceTableName().toLowerCase())
            );
            
            // 包装 ResultSet 实现自动解密
            ResultSet wrappedRs = ResultSetDecryptingProxy.wrap(
                rs, tables, parseResult, sql
            );
            
            // 提取数据
            List<String> columns = new ArrayList<>();
            List<Map<String, Object>> rows = new ArrayList<>();
            
            ResultSetMetaData metaData = wrappedRs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            for (int i = 1; i <= columnCount; i++) {
                columns.add(metaData.getColumnLabel(i));
            }
            
            while (wrappedRs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (String column : columns) {
                    row.put(column, wrappedRs.getObject(column));
                }
                rows.add(row);
            }
            
            return new QueryResult(columns, rows);
            
        } catch (SQLException e) {
            throw new RuntimeException("查询执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取配置信息
     */
    public ConfigInfo getConfigInfo() {
        ConfigInfo config = new ConfigInfo();
        
        // 获取表配置
        List<TableConfigInfo> tables = new ArrayList<>();
        Set<String> tableNames = TableCache.getTables();
        for (String tableName : tableNames) {
            List<String> fields = TableCache.getTableFieldName(tableName);
            tables.add(new TableConfigInfo(tableName, fields));
        }
        config.setTables(tables);
        
        // 获取缓存状态
        CacheInfo cacheInfo = new CacheInfo();
        cacheInfo.setSqlParseCache(SqlParseCache.size() + "/" + SqlParseCache.getMaxSize());
        cacheInfo.setStrategyCache(StrategyCache.size() + "");
        config.setCache(cacheInfo);
        
        return config;
    }
}
```

---

## 🔒 安全配置

### 拦截器配置

```java
@Configuration
public class MonitorConfig implements WebMvcConfigurer {

    @Autowired
    private MonitorProperties properties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (properties.isEnabled()) {
            registry.addInterceptor(new MonitorAuthInterceptor())
                .addPathPatterns("/monitor/api/**")
                .excludePathPatterns("/monitor/login", "/monitor/logout", "/monitor/index.html");
        }
    }
}

public class MonitorAuthInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("monitor_logged_in") == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }
}
```

---

## 📦 使用方式

### 1. 添加依赖

如果监控功能在 `securt-kit-starter` 中，用户只需要添加 starter 依赖即可。

### 2. 配置启用

```yaml
securtkit:
  monitor:
    enabled: true
    username: admin
    password: admin123
```

### 3. 访问页面

访问：`http://localhost:8080/monitor/index.html`

---

## 🎯 功能特点

1. **简单实用**：页面内嵌，无需额外部署
2. **配置简单**：用户名密码写死在配置文件
3. **轻量级**：使用原生 HTML/CSS/JS，无需构建
4. **参考 Druid**：采用类似的实现方式
5. **开箱即用**：添加依赖后配置即可使用

---

## 📝 注意事项

1. **生产环境安全**：
   - 必须修改默认密码
   - 建议限制访问 IP（通过 Nginx 或 Spring Security）
   - 建议使用 HTTPS

2. **性能考虑**：
   - 查询解密功能会直接使用应用数据源，注意性能影响
   - 可以考虑异步处理大量数据

3. **功能限制**：
   - 查询解密功能需要应用配置了数据源
   - SQL 转换功能仅支持简单的 INSERT/UPDATE

---

**设计完成时间**：2025-01-XX  
**版本**：v2.0（简化版）
