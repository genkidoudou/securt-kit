# MyBatis 拦截模式与 JDBC 模式融合设计方案

> 状态：**已实现（P0 + P1 + MP Wrapper）**  
> 版本：v1.2  
> 更新日期：2026-09-05  
> 相关讨论：在现有 JDBC 拦截（模式 ①）基础上，增加 MyBatis Interceptor + 配置（模式 ③），配置与加解密内核共用，通过配置切换启用通道。

**实现落点**：
- `securt-kit-core`：`FieldCryptoService` / `EncryptModeHolder` / `FieldEncryptorProperties.Mode`
- `securt-kit-mybatis`：`EncryptInterceptor`（P0 实体读写 + P1 等值/IN/Map + MP `QueryWrapper`/`UpdateWrapper`）
- `starter-boot2/3`：按 `mode` 条件装配 + 启动互斥校验

---

## 1. 背景与目标

### 1.1 现有能力

Securt-Kit 当前基于 **JDBC Driver 代理** 实现透明字段加解密：

- 数据源使用 `SimpleInterceptorDriver` + `jdbc:interceptor:` URL 前缀
- 加密字段清单由 `securtkit.encryptor` YAML 配置
- 核心组件：`FieldEncryptorProperties`、`ConfigInitializer`、`TableCache`、`StrategyCache`、`FieldEncryptorStrategy`、`ParameterEncryptor`、`ResultSetDecryptingProxy` 等

### 1.2 新增目标

1. **配置开关**：支持在 **JDBC 模式（①）** 与 **MyBatis Interceptor 模式（③）** 之间切换  
2. **加密融合**：两种模式共用同一套策略、失败策略、表字段配置与加解密语义  
3. **配置共用**：只保留一份 `FieldEncryptorProperties`，禁止为 MyBatis 平行复制配置模型  

### 1.3 非目标（本阶段不做）

- LIKE / 函数下推到密文列的「透明」支持（见第 8 节）  
- ① 与 ③ 在同一数据源上同时生效（默认禁止，防双重加密）

---

## 2. 两种模式对比（决策摘要）

| 维度 | ① JDBC 拦截（现有） | ③ MyBatis Interceptor + 配置（规划） |
|------|---------------------|--------------------------------------|
| 挂载点 | Driver / Statement / ResultSet | MyBatis Plugin（Parameter / Result） |
| 加密清单 | YAML | YAML（同一份） |
| 业务 DO | 可不改 | 可不改 |
| 数据源 | 必须改驱动与 URL | **不必改** |
| 覆盖范围 | 所有 JDBC | **仅 MyBatis 路径** |
| JdbcTemplate / 纯 JDBC | ✅ | ❌ |
| 与分页/多租户插件 | 无插件序问题 | **需处理 Interceptor 顺序** |
| 集成成本 | 基础设施侵入较高 | 对已有连接池更友好 |

**选型建议**：

- 需要覆盖非 MyBatis 访问 → 使用 `mode=JDBC`
- 栈以 MyBatis/MP 为主、不愿改驱动 → 使用 `mode=MYBATIS`
- **Monitor**：SQL 查询双视图与单行验签通过「读库原值 + 结果侧按配置解密」工作，**不要求** JDBC 通道；`mode=MYBATIS` 下同样可用（复杂 JOIN/别名仅最佳努力匹配列名）

---

## 3. 配置设计（共用 + 模式开关）

### 3.1 配置示例

```yaml
securtkit:
  encryptor:
    enable: true
    mode: JDBC          # JDBC | MYBATIS | OFF
    failure-policy: FALLBACK
    sql-parse-cache:
      enable: true
      max-size: 1000
    skip-comment:
      enable: false
      token: SECURT_SKIP
    ignore-table-case: true
    tables:
      - table-name: user
        datasource-id: primary   # 多数据源可选
        fields:
          - field-name: phone
            strategy: com.example.AesEncryptorStrategy
          - field-name: id_card
```

### 3.2 `mode` 语义

| 值 | 行为 |
|----|------|
| `JDBC` | 默认值，兼容现状；启用 Driver 拦截通道；**不**注册 MyBatis Plugin |
| `MYBATIS` | 注册 MyBatis `EncryptInterceptor`；不要求使用 `jdbc:interceptor:` URL |
| `OFF` | 加载配置但不挂载任何拦截通道（便于排障） |

### 3.3 配置类扩展（不新建平行 Properties）

继续使用 core 中的 `FieldEncryptorProperties`，仅扩展字段：

```java
public class FieldEncryptorProperties {
    public static final String PREFIX = "securtkit.encryptor";

    private boolean enable;
    private Mode mode = Mode.JDBC;   // 新增

    private FailurePolicy failurePolicy;
    private SqlParseCacheConfig sqlParseCache;
    private SkipCommentConfig skipComment;
    private boolean ignoreTableCase;
    private List<TableConfig> tables;

    public enum Mode {
        JDBC,
        MYBATIS,
        OFF
    }
}
```

### 3.4 配置项共用矩阵

| 配置项 | JDBC | MYBATIS | 说明 |
|--------|------|---------|------|
| `enable` / `mode` | ✅ | ✅ | 总开关与通道选择 |
| `tables` / `strategy` / `datasource-id` | ✅ | ✅ | **同一份加密清单** |
| `failure-policy` | ✅ | ✅ | 由统一门面执行 |
| `sql-parse-cache` | ✅ | ✅ | 若 Plugin 也解析 SQL，共用 `SqlParseCache` |
| `skip-comment` | ✅ | ✅ | Plugin 侧应对 BoundSql 注释语义对齐 |
| `ignore-table-case` | ✅ | ✅ | 表名匹配规则一致 |

**禁止**：为 MyBatis 再定义 `MybatisEncryptorProperties` 并复制 `tables`。

### 3.5 启动互斥校验

Starter 在自动配置阶段校验，避免双重加密或「以为开了实际未生效」：

| 条件 | 建议行为 |
|------|----------|
| `mode=MYBATIS` 且 JDBC URL 含 `jdbc:interceptor:` | **启动失败**或强告警（推荐失败） |
| `mode=JDBC` 且未使用拦截驱动 / 无 interceptor URL | 告警：加密可能未生效 |
| `mode=MYBATIS` 但 classpath 无 MyBatis / 未引入 mybatis 模块 | 启动失败并提示依赖 |

---

## 4. 融合架构

### 4.1 分层示意

```text
┌──────────────────────────────────────────────────────┐
│  securt-kit-core（共用内核）                          │
│  - FieldEncryptorProperties / ConfigInitializer      │
│  - TableCache / StrategyCache / EncryptionHandler    │
│  - FieldEncryptorStrategy                            │
│  - FieldCryptoService（统一加解密门面，新增）          │
│  - SecurtkitUtils / SqlParseCache（①③均可复用）       │
└──────────────────────────────────────────────────────┘
              ▲                         ▲
              │                         │
┌─────────────┴────────────┐  ┌─────────┴─────────────────┐
│ JDBC 通道（现有）         │  │ MyBatis 通道（新增）       │
│ SimpleInterceptor*       │  │ EncryptInterceptor        │
│ 识别 SQL 参数/结果列     │  │ 识别 BoundSql / 结果映射  │
│ → 调用 FieldCryptoService│  │ → 调用 FieldCryptoService │
└─────────────┬────────────┘  └─────────┬─────────────────┘
              │                         │
              └────────────┬────────────┘
                           ▼
              starter-boot2 / starter-boot3
              - 绑定 Properties
              - TableCache.init（只一次）
              - 按 mode 条件装配通道
              - 互斥校验
              - Monitor（读同一份配置）
```

### 4.2 统一门面：`FieldCryptoService`

将散落在 `ParameterEncryptor`、`ResultSetDecryptingProxy` 中的「查配置 → 调策略 → 失败策略 → 日志」收敛到门面，供两种通道调用：

```java
public interface FieldCryptoService {

    boolean needEncrypt(String table, String column, String datasourceId);

    String encrypt(String table, String column, String plain, String datasourceId);

    String decrypt(String table, String column, String cipher, String datasourceId);
}
```

**实现约束**：

- 内部继续使用 `TableCache`、`StrategyCache`、`EncryptionHandler`
- `decrypt` 支持明文兼容（迁移期：解密失败且像明文则原样返回）
- `encrypt` 支持防二次加密（已是密文特征则跳过，策略可配置）
- ① 与 ③ **不得**各自再实现一套策略调用逻辑

### 4.3 JDBC 通道改造要点

- `SimpleInterceptor*` / `ParameterEncryptor` / `ResultSetDecryptingProxy` 改为调用 `FieldCryptoService`
- 行为与现网保持兼容；默认 `mode=JDBC`
- 当 `mode != JDBC` 时：不启用加密逻辑（或要求业务侧不使用 interceptor URL）

### 4.4 MyBatis 通道要点

- 新模块（建议）`securt-kit-mybatis`，对 MyBatis 使用 optional/provided 依赖
- Plugin 只负责：**识别 table + column + value**，加解密一律交给 `FieldCryptoService`
- 字段识别可复用 `SecurtkitUtils.parseSql` + `SqlParseCache`，与 JDBC 语义尽量对齐
- 需明确与 PageHelper、MyBatis-Plus 分页、数据权限等插件的执行顺序

---

## 5. 模块与依赖规划

| 模块 | 职责 |
|------|------|
| `securt-kit-core` | 配置、缓存、策略、`FieldCryptoService`、SQL 解析；现有 JDBC 拦截短期可保留于此 |
| `securt-kit-mybatis`（新建） | MyBatis `EncryptInterceptor` 及辅助类 |
| `securt-kit-jdbc`（可选中期拆分） | 将 Driver 拦截从 core 迁出，由 mode 控制启用 |
| `securt-kit-starter-boot2/3` | 自动配置、mode 开关、互斥校验、监控 UI |

业务依赖示例：

```xml
<!-- JDBC 模式：现有 starter 即可 -->
<dependency>
    <groupId>io.github.genkidoudou</groupId>
    <artifactId>securt-kit-starter-boot2</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<!-- MYBATIS 模式：starter + mybatis 模块（或以 starter 传递 optional） -->
<dependency>
    <groupId>io.github.genkidoudou</groupId>
    <artifactId>securt-kit-mybatis</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

装配条件建议：

- `securtkit.encryptor.enable=true`
- `securtkit.encryptor.mode=MYBATIS`
- classpath 存在 MyBatis 相关类

---

## 6. Starter 自动配置流程

```text
SecurtKitAutoConfiguration
  │
  ├─ @EnableConfigurationProperties(FieldEncryptorProperties)
  ├─ TableCache.init(properties)     // ConfigInitializer，全局一次
  ├─ 注册 FieldCryptoService Bean
  ├─ 互斥校验（mode vs URL vs classpath）
  │
  ├─ mode == JDBC
  │     └─ JDBC 通道生效（现状 Driver 拦截）
  │
  ├─ mode == MYBATIS
  │     └─ 注册 Mybatis EncryptInterceptor
  │
  ├─ mode == OFF
  │     └─ 不挂载通道
  │
  └─ MonitorAutoConfiguration
        └─ 始终可读同一份 FieldEncryptorProperties / FieldCryptoService
```

---

## 7. 实现分期（P0～P2）

> LIKE / 函数检索增强视为 P3，不在本方案交付范围内。

| 阶段 | 目标 | 难度 | 说明 |
|------|------|------|------|
| **P0** | 实体 insert/update 加密；select 映射解密 | 低～中 | MyBatis 主路径可用 |
| **P1** | WHERE `=` / `IN` 参数加密；`Map` 结果按列解密 | 中 | 等值查询对齐 JDBC 能力 |
| **P2** | 动态 SQL、多表别名、MP Wrapper、批量、多数据源 | 中高 | 靠用例矩阵打磨，防漏加密 |

### 7.1 推荐落地顺序

1. 抽取 `FieldCryptoService`，JDBC 通道全部改为走门面（行为不变，用现有 test 回归）  
2. `FieldEncryptorProperties` 增加 `mode`，默认 `JDBC`  
3. 新增 `securt-kit-mybatis`，完成 **P0**  
4. Starter 条件装配 + 双重加密互斥校验  
5. 推进 **P1 → P2**（按场景分批，如先单表 MP Wrapper，再多表别名）  

### 7.2 难度说明（有现有 core 时）

- 加解密与配置 **复用现有实现**，难度主要在 MyBatis 字段识别与插件兼容  
- P0+P1：约可做到主路径可用  
- P0～P2：属于中等偏上工程量，需完整集成测试，而非两天可扫完  

### 7.3 P2 高风险点清单

1. 与分页插件的 Interceptor 顺序  
2. ~~MyBatis-Plus `QueryWrapper` / `ew` 参数结构~~（已实现：`MpWrapperParamSupport`）  
3. 动态 SQL / `foreach` 多占位符（主路径已覆盖，边角仍待打磨）  
4. 多表同名列（`u.phone` vs `o.phone`）  
5. `ExecutorType.BATCH`  
6. 多数据源与 `datasource-id` 上下文绑定（MYBATIS 通道已接 `DatasourceIdResolver`）

---

## 8. LIKE、函数等场景（与模式无关）

常规 AES/SM4 等应用层加密后，**密文列无法在库内按明文语义**做 `LIKE` / `UPPER` / `SUBSTRING`。① 与 ③ 都不改写 SQL 去「假装」明文函数语义。

| 场景 | 建议 |
|------|------|
| 等值查询 | 支持（P1）；可用盲索引列 HMAC 增强 |
| `LIKE` / 模糊 | 旁路检索列 / ES / n-gram；禁止对密文 `LIKE` |
| `UPPER` / `SUBSTRING` 等 | 应用内计算，或冗余业务列；若仍写在 SELECT 上，结果侧可能按源列尝试解密**函数返回值**，改写密文后常 FALLBACK |
| SELECT `IFNULL`/`TRIM`/`CAST` 等 | **结果侧支持**：抽出第一个源加密列并解密返回值（JDBC / MYBATIS / Monitor）；多加密列取第一个 |
| SELECT `CASE` / `LAG`/`LEAD` 等窗口列表达式 / Binary(`\|\|` 等) / 标量子查询投影 | **结果侧支持**（同上抽列规则）；`ROW_NUMBER()` 等无列返回值不解密 |
| `ORDER BY` 加密列 | 密文序 ≠ 明文序；需排序列或应用排序 |

配置可预留扩展（未来 P3），例如：

```yaml
fields:
  - field-name: name
    strategy: xxx.AesStrategy
    search:
      type: NONE    # NONE | HASH | NGRAM
      index-field: name_idx
```

---

## 9. 设计约束小结

1. **单一配置模型**：仅 `FieldEncryptorProperties` + `ConfigInitializer` / `TableCache`  
2. **单一加解密入口**：`FieldCryptoService`  
3. **通道可插拔**：由 `mode` 选择 JDBC 或 MYBATIS，默认 JDBC 保证兼容  
4. **禁止双开**：同一数据源不得同时启用 ① 与 ③  
5. **Monitor / 刷数 / 试加密**：走共用配置与门面，与 mode 无关  

---

## 10. 文档与后续

- 实现完成后应补充：快速开始中的 `mode` 说明、MYBATIS 模式专用集成步骤、插件顺序注意事项  
- 相关现有文档：  
  - [快速开始](QUICK-START.md)  
  - [使用指南](USAGE.md)  
  - [多数据源配置](MULTI-DATASOURCE.md)  
  - [Core 模块说明](../securt-kit-core/README.md)  

---

## 修订记录

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-09-04 | v1.0 | 初稿：模式切换、融合架构、配置共用、P0～P2 分期 |
| 2026-09-05 | v1.1 | 落地 P0+P1：FieldCryptoService、securt-kit-mybatis、Starter 互斥校验 |
| 2026-09-05 | v1.2 | 落地 MP QueryWrapper/UpdateWrapper：`MpWrapperParamSupport` 直写 `paramNameValuePairs` |
