# Field Digest Integrity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Securt-Kit 增加表级完整性摘要：写时按明文计算并落库 `target-field`（可单表改写 SQL），读时可选验签；JDBC 与 MYBATIS 共用配置与 `DigestService`。

**Architecture:** 配置进入 `FieldEncryptorProperties` + `DigestConfigRegistry`；计算/补读/验签集中在 `DigestService`；`DigestSqlRewriter` 只负责单表 INSERT/UPDATE 追加列；JDBC 在 `Connection.prepareStatement` **之前**改写 SQL，在 execute 前注入摘要参数再走现有加密；MYBATIS 在 `ParameterEncryptHelper` / `ResultDecryptHelper` 调用同一门面。策略通过扩展 `FieldEncryptorStrategy`（default `digest`/`verifyDigest`/`supportsDigest`）实现，内置 HMAC-SHA256。

**Tech Stack:** Java 8、JUnit 5、jsqlparser 4.9（已 shade）、Hutool、Spring Boot `@ConfigurationProperties`（optional）、MyBatis Interceptor（`securt-kit-mybatis`）。

**Spec:** `docs/superpowers/specs/2026-09-05-field-digest-integrity-design.md`

## Global Constraints

- Java 8 only（`maven.compiler.source/target` = 8）；禁止使用 `var`、records、`List.of` 等 Java 9+ API。
- 摘要输入必须是明文；顺序：digest → encrypt（写）；decrypt → verify（读）。
- `target-field` 不得出现在同表加密 `fields` 中；摘要列本身不加密。
- 单表才允许 SQL 改写；多表 / 解析失败 → warn，不改写。
- `partial-update` 默认 `RELOAD`；INSERT 无法补读时 `RELOAD` ≡ `FAIL`。
- 默认 profile 下 `maven.test.skip=true`；跑测必须加 `-Dmaven.test.skip=false`。
- 第一期不做：JOIN 验签、BATCH 完整语义、监控试算 UI、独立 `DigestStrategy` 接口。
- HMAC 密钥配置项：`securtkit.encryptor.digest-hmac-key`（使用内置 HMAC 策略时必填，启动校验）。

---

## File Structure

| Path | Responsibility |
|------|----------------|
| `securt-kit-core/.../config/FieldEncryptorProperties.java` | 全局 digest 默认项、`DigestConfig`、`PartialUpdate` 枚举 |
| `securt-kit-core/.../config/DigestConfigRegistry.java` | 运行时按表查询已解析的 digest 组 |
| `securt-kit-core/.../config/ConfigInitializer.java` | 校验 + 注册 digest |
| `securt-kit-core/.../strategy/FieldEncryptorStrategy.java` | `supportsDigest` / `digest` / `verifyDigest` |
| `securt-kit-core/.../strategy/HmacSha256DigestStrategy.java` | 内置 HMAC-SHA256 摘要 |
| `securt-kit-core/.../digest/DigestService.java` | 计算、partial-update、验签、失败策略 |
| `securt-kit-core/.../digest/DigestSqlRewriter.java` | 单表 INSERT/UPDATE 追加 `target-field` + `?` |
| `securt-kit-core/.../digest/DigestRewriteResult.java` | 改写结果 DTO（新 SQL、追加参数索引） |
| `securt-kit-core/.../digest/ResolvedDigestRule.java` | 解析后的一条 digest 规则 |
| `securt-kit-core/.../exception/DigestMismatchException.java` | 验签失败（FAIL_FAST） |
| `securt-kit-core/.../interceptor/SimpleInterceptorConnection.java` | prepare 前改写 SQL |
| `securt-kit-core/.../interceptor/SimpleInterceptorPreparedStatement.java` | execute 前注入摘要参数 |
| `securt-kit-core/.../interceptor/ResultSetDecryptingProxy.java` | 可选验签 |
| `securt-kit-mybatis/.../ParameterEncryptHelper.java` | 写摘要 + BoundSql 改写 |
| `securt-kit-mybatis/.../ResultDecryptHelper.java` | 读验签 |
| `docs/USAGE.md` + spec 状态 | 用户文档 |

---

### Task 1: 配置模型与枚举

**Files:**
- Modify: `securt-kit-core/src/main/java/io/github/hexlodev/core/config/FieldEncryptorProperties.java`
- Create: `securt-kit-core/src/test/java/io/github/hexlodev/core/config/DigestConfigModelTest.java`
- Modify: `securt-kit-core/pom.xml`（补 junit-jupiter test 依赖 + surefire）

**Interfaces:**
- Consumes: 无
- Produces:
  - `FieldEncryptorProperties.PartialUpdate`：`SKIP`, `RELOAD`, `FAIL`
  - `FieldEncryptorProperties.DigestConfig`：`List<String> sourceFields`, `String targetField`, `String strategy`, `PartialUpdate partialUpdate`, `Boolean verifyOnRead`, `FailurePolicy failurePolicy`
  - 全局字段：`String digestStrategy`, `PartialUpdate digestPartialUpdate`（默认 RELOAD）, `boolean digestVerifyOnRead`（默认 false）, `FailurePolicy digestFailurePolicy`（可 null）, `String digestHmacKey`, `TableConfig.digest`：`List<DigestConfig>`

- [ ] **Step 1: 为 core 增加测试依赖**

在 `securt-kit-core/pom.xml` 的 `</dependencies>` 前加入：

```xml
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
        </dependency>
```

在 `<build><plugins>` 中加入：

```xml
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
```

- [ ] **Step 2: 写失败测试（默认值）**

```java
package io.github.genkidoudou.core.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DigestConfigModelTest {

    @Test
    void globalDigestDefaults() {
        FieldEncryptorProperties p = new FieldEncryptorProperties();
        assertEquals(FieldEncryptorProperties.PartialUpdate.RELOAD, p.getDigestPartialUpdate());
        assertFalse(p.isDigestVerifyOnRead());
        assertNull(p.getDigestFailurePolicy());
    }

    @Test
    void tableCanHoldDigestList() {
        FieldEncryptorProperties.TableConfig t = new FieldEncryptorProperties.TableConfig();
        FieldEncryptorProperties.DigestConfig d = new FieldEncryptorProperties.DigestConfig();
        d.setSourceFields(java.util.Arrays.asList("phone", "id_card"));
        d.setTargetField("row_digest");
        t.setDigest(java.util.Collections.singletonList(d));
        assertEquals(1, t.getDigest().size());
        assertEquals("row_digest", t.getDigest().get(0).getTargetField());
    }
}
```

- [ ] **Step 3: 跑测确认失败（缺字段）**

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestConfigModelTest test`

Expected: 编译失败或断言失败（尚无 `digestPartialUpdate` 等字段）

- [ ] **Step 4: 实现配置字段**

在 `FieldEncryptorProperties` 中 `failurePolicy` 附近增加：

```java
    private String digestStrategy;
    private PartialUpdate digestPartialUpdate = PartialUpdate.RELOAD;
    private boolean digestVerifyOnRead = false;
    private FailurePolicy digestFailurePolicy;
    private String digestHmacKey;
```

在 `TableConfig` 中 `fields` 后增加：

```java
        private List<DigestConfig> digest;
```

新增内部类与枚举：

```java
    @Data
    public static class DigestConfig {
        private List<String> sourceFields;
        private String targetField;
        private String strategy;
        private PartialUpdate partialUpdate;
        private Boolean verifyOnRead;
        private FailurePolicy failurePolicy;
    }

    public enum PartialUpdate {
        SKIP, RELOAD, FAIL;

        public static PartialUpdate fromString(String value) {
            if (value == null || value.trim().isEmpty()) {
                return RELOAD;
            }
            try {
                return valueOf(value.toUpperCase(Locale.ROOT).trim());
            } catch (IllegalArgumentException e) {
                return RELOAD;
            }
        }
    }
```

（补 `import java.util.Locale;`）

- [ ] **Step 5: 跑测通过并提交**

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestConfigModelTest test`  
Expected: PASS

```bash
git add securt-kit-core/pom.xml securt-kit-core/src/main/java/io/github/hexlodev/core/config/FieldEncryptorProperties.java securt-kit-core/src/test/java/io/github/hexlodev/core/config/DigestConfigModelTest.java
git commit -m "feat(digest): add digest config model and defaults"
```

---

### Task 2: 策略接口扩展 + HMAC 实现

**Files:**
- Modify: `securt-kit-core/src/main/java/io/github/hexlodev/core/strategy/FieldEncryptorStrategy.java`
- Create: `securt-kit-core/src/main/java/io/github/hexlodev/core/strategy/HmacSha256DigestStrategy.java`
- Create: `securt-kit-core/src/test/java/io/github/hexlodev/core/strategy/HmacSha256DigestStrategyTest.java`

**Interfaces:**
- Consumes: Task 1 配置中的 `digestHmacKey`（构造注入）
- Produces:
  - `default boolean supportsDigest()` → `false`
  - `default String digest(Map<String, String> sourcePlainValues)` → `null`
  - `default boolean verifyDigest(Map<String, String> sourcePlainValues, String digestValue)`
  - `HmacSha256DigestStrategy(String key)`：`supportsDigest()==true`；`encryption`/`decryption` 抛 `UnsupportedOperationException`（仅作 digest 策略）

- [ ] **Step 1: 写失败测试**

```java
package io.github.genkidoudou.core.strategy;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HmacSha256DigestStrategyTest {

    @Test
    void digestIsDeterministicAndVerifies() {
        HmacSha256DigestStrategy s = new HmacSha256DigestStrategy("test-secret");
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("phone", "13800138000");
        m.put("id_card", "110101199001011234");
        String d1 = s.digest(m);
        String d2 = s.digest(m);
        assertNotNull(d1);
        assertEquals(d1, d2);
        assertTrue(s.verifyDigest(m, d1));
        m.put("phone", "13900139000");
        assertFalse(s.verifyDigest(m, d1));
    }

    @Test
    void orderMatters() {
        HmacSha256DigestStrategy s = new HmacSha256DigestStrategy("test-secret");
        Map<String, String> a = new LinkedHashMap<String, String>();
        a.put("phone", "1");
        a.put("id_card", "2");
        Map<String, String> b = new LinkedHashMap<String, String>();
        b.put("id_card", "2");
        b.put("phone", "1");
        assertNotEquals(s.digest(a), s.digest(b));
    }
}
```

- [ ] **Step 2: 跑测确认失败**

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=HmacSha256DigestStrategyTest test`  
Expected: 找不到类 / 编译失败

- [ ] **Step 3: 扩展接口**

在 `FieldEncryptorStrategy` 末尾（`decryption` 之后）增加：

```java
    default boolean supportsDigest() {
        return false;
    }

    default String digest(java.util.Map<String, String> sourcePlainValues) {
        return null;
    }

    default boolean verifyDigest(java.util.Map<String, String> sourcePlainValues, String digestValue) {
        if (digestValue == null || sourcePlainValues == null) {
            return false;
        }
        String expected = digest(sourcePlainValues);
        if (expected == null) {
            return false;
        }
        byte[] a = expected.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = digestValue.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(a, b);
    }
```

- [ ] **Step 4: 实现 HMAC 策略**

```java
package io.github.genkidoudou.core.strategy;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class HmacSha256DigestStrategy implements FieldEncryptorStrategy {

    private final byte[] keyBytes;

    public HmacSha256DigestStrategy(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("digest-hmac-key must not be blank");
        }
        this.keyBytes = key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String encryption(String oldValue) {
        throw new UnsupportedOperationException("HmacSha256DigestStrategy is digest-only");
    }

    @Override
    public String decryption(String oldValue) {
        throw new UnsupportedOperationException("HmacSha256DigestStrategy is digest-only");
    }

    @Override
    public boolean supportsDigest() {
        return true;
    }

    @Override
    public String digest(Map<String, String> sourcePlainValues) {
        if (sourcePlainValues == null || sourcePlainValues.isEmpty()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : sourcePlainValues.entrySet()) {
                sb.append(e.getKey()).append('=').append(e.getValue() == null ? "" : e.getValue()).append('\n');
            }
            byte[] raw = mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC digest failed", ex);
        }
    }
}
```

注意：`StrategyCache` 默认无参构造；HMAC 策略需在 `ConfigInitializer`（Task 3）里用 `digestHmacKey` **手动** `StrategyCache.registerStrategy(...)`，不要依赖反射无参 new。

- [ ] **Step 5: 跑测通过并提交**

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=HmacSha256DigestStrategyTest test`  
Expected: PASS

```bash
git add securt-kit-core/src/main/java/io/github/hexlodev/core/strategy/FieldEncryptorStrategy.java securt-kit-core/src/main/java/io/github/hexlodev/core/strategy/HmacSha256DigestStrategy.java securt-kit-core/src/test/java/io/github/hexlodev/core/strategy/HmacSha256DigestStrategyTest.java
git commit -m "feat(digest): extend FieldEncryptorStrategy with HMAC digest"
```

---

### Task 3: DigestConfigRegistry + 启动校验

**Files:**
- Create: `securt-kit-core/src/main/java/io/github/hexlodev/core/digest/ResolvedDigestRule.java`
- Create: `securt-kit-core/src/main/java/io/github/hexlodev/core/config/DigestConfigRegistry.java`
- Modify: `securt-kit-core/src/main/java/io/github/hexlodev/core/config/ConfigInitializer.java`
- Create: `securt-kit-core/src/test/java/io/github/hexlodev/core/config/DigestConfigRegistryTest.java`

**Interfaces:**
- Consumes: Task 1/2 类型与 `StrategyCache`
- Produces:
  - `ResolvedDigestRule`：tableName、sourceFields、targetField、strategyClass、partialUpdate、verifyOnRead、failurePolicy
  - `DigestConfigRegistry.register(String datasourceId, String table, List<ResolvedDigestRule>)`
  - `DigestConfigRegistry.getRules(String table, String datasourceId)` → `List<ResolvedDigestRule>`
  - `DigestConfigRegistry.clear()`
  - `ConfigInitializer` 在校验/注册阶段写入 registry；`reset` 时 `clear()`

- [ ] **Step 1: 写失败测试**

```java
package io.github.genkidoudou.core.config;

import io.github.genkidoudou.core.cache.StrategyCache;
import io.github.genkidoudou.core.strategy.HmacSha256DigestStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class DigestConfigRegistryTest {

    @AfterEach
    void tearDown() {
        ConfigInitializer.reset();
        DigestConfigRegistry.clear();
        StrategyCache.clear();
    }

    @Test
    void rejectsTargetFieldAlsoEncrypted() {
        FieldEncryptorProperties p = baseProps();
        FieldEncryptorProperties.FieldConfig f = new FieldEncryptorProperties.FieldConfig();
        f.setFieldName("row_digest");
        p.getTables().get(0).getFields().add(f);
        assertThrows(RuntimeException.class, () -> ConfigInitializer.initialize(p));
    }

    @Test
    void registersDigestRules() {
        FieldEncryptorProperties p = baseProps();
        ConfigInitializer.initialize(p);
        assertFalse(DigestConfigRegistry.getRules("user", "default").isEmpty());
        assertEquals("row_digest", DigestConfigRegistry.getRules("user", "default").get(0).getTargetField());
    }

    private FieldEncryptorProperties baseProps() {
        StrategyCache.registerStrategy(HmacSha256DigestStrategy.class, new HmacSha256DigestStrategy("k"));
        FieldEncryptorProperties p = new FieldEncryptorProperties();
        p.setEnable(true);
        p.setDigestStrategy(HmacSha256DigestStrategy.class.getName());
        p.setDigestHmacKey("k");
        FieldEncryptorProperties.TableConfig t = new FieldEncryptorProperties.TableConfig();
        t.setTableName("user");
        FieldEncryptorProperties.FieldConfig phone = new FieldEncryptorProperties.FieldConfig();
        phone.setFieldName("phone");
        t.setFields(new java.util.ArrayList<FieldEncryptorProperties.FieldConfig>(Collections.singletonList(phone)));
        FieldEncryptorProperties.DigestConfig d = new FieldEncryptorProperties.DigestConfig();
        d.setSourceFields(Arrays.asList("phone"));
        d.setTargetField("row_digest");
        t.setDigest(Collections.singletonList(d));
        p.setTables(Collections.singletonList(t));
        return p;
    }
}
```

- [ ] **Step 2: 跑测确认失败**

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestConfigRegistryTest test`  
Expected: FAIL（缺 registry / 校验）

- [ ] **Step 3: 实现 ResolvedDigestRule + Registry**

`ResolvedDigestRule` 字段：`tableName`, `sourceFields`, `targetField`, `strategyClass`, `partialUpdate`, `verifyOnRead`, `failurePolicy`（不可变 + getters）。

`DigestConfigRegistry`：`datasourceId → tableName(lower) → List<ResolvedDigestRule>`；提供 `register` / `getRules` / `clear` / `hasDigest(table, ds)`。

- [ ] **Step 4: 在 ConfigInitializer 中校验并注册**

1. `sourceFields` 非空；`targetField` 非空  
2. 同表 `targetField` 不重复（ignore case）  
3. `targetField` 不在同表加密 fields 中  
4. strategy：项级 → 全局 `digestStrategy`；为空失败  
5. 若为 `HmacSha256DigestStrategy`：`digestHmacKey` 非空并 `registerStrategy`  
6. `supportsDigest()` 必须为 true  
7. 合并 partialUpdate / verifyOnRead / failurePolicy（项 → 全局 → 默认；failurePolicy 最终回落到 `failurePolicy`）

`reset()` 末尾调用 `DigestConfigRegistry.clear()`。

- [ ] **Step 5: 跑测通过并提交**

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestConfigRegistryTest test`  
Expected: PASS

```bash
git add securt-kit-core/src/main/java/io/github/hexlodev/core/digest/ResolvedDigestRule.java securt-kit-core/src/main/java/io/github/hexlodev/core/config/DigestConfigRegistry.java securt-kit-core/src/main/java/io/github/hexlodev/core/config/ConfigInitializer.java securt-kit-core/src/test/java/io/github/hexlodev/core/config/DigestConfigRegistryTest.java
git commit -m "feat(digest): register and validate digest configs at startup"
```

---

### Task 4: DigestSqlRewriter（纯函数）

**Files:**
- Create: `securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestRewriteResult.java`
- Create: `securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestSqlRewriter.java`
- Create: `securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestSqlRewriterTest.java`

**Interfaces:**
- Consumes: jsqlparser
- Produces:
  - `DigestRewriteResult { String sql; List<Integer> appendedParameterIndexes; boolean rewritten; String warnMessage; }`
  - `DigestSqlRewriter.tryAppendTargets(String sql, List<String> missingTargetFields)`  
    - 仅单表 Insert / Update  
    - INSERT：列末尾加列，VALUES 加 `?`  
    - UPDATE：SET 末尾加 `col=?`（索引 = 原 SET 占位符数 + 1）  
    - 已有 target：不重复追加  
    - 多表 / 解析失败：`rewritten=false` + warnMessage

- [ ] **Step 1: 写失败测试**

```java
package io.github.genkidoudou.core.digest;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class DigestSqlRewriterTest {

    @Test
    void appendInsertColumn() {
        DigestRewriteResult r = DigestSqlRewriter.tryAppendTargets(
                "INSERT INTO user (phone, name) VALUES (?, ?)",
                Collections.singletonList("row_digest"));
        assertTrue(r.isRewritten());
        assertTrue(r.getSql().toLowerCase().contains("row_digest"));
        assertEquals(Collections.singletonList(Integer.valueOf(3)), r.getAppendedParameterIndexes());
    }

    @Test
    void appendUpdateSet() {
        DigestRewriteResult r = DigestSqlRewriter.tryAppendTargets(
                "UPDATE user SET phone = ? WHERE id = ?",
                Collections.singletonList("row_digest"));
        assertTrue(r.isRewritten());
        assertTrue(r.getSql().toLowerCase().contains("row_digest"));
        // 新 ? 在 SET 末尾、WHERE 之前 → 索引 2；WHERE id 顺延为 3
        assertEquals(Collections.singletonList(Integer.valueOf(2)), r.getAppendedParameterIndexes());
    }

    @Test
    void skipMultiTable() {
        DigestRewriteResult r = DigestSqlRewriter.tryAppendTargets(
                "UPDATE user u JOIN t ON u.id=t.id SET u.phone = ?",
                Collections.singletonList("row_digest"));
        assertFalse(r.isRewritten());
        assertNotNull(r.getWarnMessage());
    }
}
```

- [ ] **Step 2: 跑测确认失败**

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestSqlRewriterTest test`  
Expected: FAIL

- [ ] **Step 3: 实现 Rewriter**

使用 `CCJSqlParserUtil.parse`：

- `Insert`：单表、有显式列；追加 `Column` + 各 `ExpressionList` 追加 `JdbcParameter`  
- `Update`：单表无 join；SET 追加列=`JdbcParameter`  
- 按 SQL 中 `?` 出现顺序计算 1-based 追加索引  

- [ ] **Step 4: 跑测通过并提交**

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestSqlRewriterTest test`  
Expected: PASS（若索引与实现不一致，以实现为准修正断言）

```bash
git add securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestSqlRewriter.java securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestRewriteResult.java securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestSqlRewriterTest.java
git commit -m "feat(digest): add single-table SQL rewriter for digest columns"
```

---

### Task 5: DigestService（计算 / partial-update / 验签）

**Files:**
- Create: `securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestService.java`
- Create: `securt-kit-core/src/main/java/io/github/hexlodev/core/exception/DigestMismatchException.java`
- Create: `securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestServiceTest.java`

**Interfaces:**
- Consumes: `DigestConfigRegistry`, `StrategyCache`, `FieldCryptoService`（解密补读列）, `ResolvedDigestRule`
- Produces:
  - `Map<String, String> computeTargetDigests(String table, String datasourceId, Map<String, String> availablePlainByColumn, boolean insert, Connection connForReload, String reloadWhereSql, List<Object> reloadWhereParams)`  
    → key=`targetField`（建议 lower），value=digest；SKIP 时省略  
  - `void verifyRow(String table, String datasourceId, Map<String, String> plainByColumn, Map<String, String> digestByTarget)`  

**RELOAD：** 调用方传入 WHERE；为空则 FAIL。SQL：`SELECT missing_cols FROM table WHERE ...`，加密列经 `FieldCryptoService.decrypt`。INSERT + 缺字段 + RELOAD → 抛错，消息含 `INSERT cannot RELOAD`。

- [ ] **Step 1: 写失败测试（纯内存）**

```java
@Test
void skipWhenPartial() {
    // rule: sources=[phone,id_card], partial=SKIP；仅 phone → 输出空
}

@Test
void failWhenPartialAndFailPolicy() {
    // partial=FAIL → throws
}

@Test
void verifyMismatchFallbackDoesNotThrow() {
    // verifyOnRead=true, FALLBACK, 错误 digest → 不抛
}

@Test
void verifyMismatchFailFastThrows() {
    // FAIL_FAST → DigestMismatchException
}
```

`@BeforeEach` 向 `DigestConfigRegistry` 注册 rule + HMAC。

- [ ] **Step 2: 实现 DigestService**

有序 Map：

```java
LinkedHashMap<String, String> ordered = new LinkedHashMap<String, String>();
for (String col : rule.getSourceFields()) {
    // ignore-case 从 available 取值
}
String digest = StrategyCache.getStrategy(rule.getStrategyClass()).digest(ordered);
```

验签：`verifyOnRead` 关闭则返回；digest null → warn；缺源 → debug；失败按 `FALLBACK`/`FAIL_FAST`/`SKIP`（`RETRY` 当 FALLBACK）。

`DigestMismatchException` 继承项目既有 `SecurtKitException`（若无则 `RuntimeException`）。

- [ ] **Step 3: 跑测通过并提交**

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestServiceTest test`  
Expected: PASS

```bash
git add securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestService.java securt-kit-core/src/main/java/io/github/hexlodev/core/exception/DigestMismatchException.java securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestServiceTest.java
git commit -m "feat(digest): add DigestService for compute and verify"
```

---

### Task 6: JDBC 写路径（prepare 前改写 + execute 前注入）

**Files:**
- Modify: `securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/SimpleInterceptorConnection.java`
- Modify: `securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/SimpleInterceptorPreparedStatement.java`
- Create: `securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestWriteSupport.java`
- Create: `securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestWriteSupportTest.java`

**Interfaces:**
- Consumes: `DigestSqlRewriter`, `DigestService`, `DigestConfigRegistry`
- Produces: JDBC 模式下 prepare **之前**改写 SQL；PS 持有 `DigestRewriteResult`；execute 前注入摘要，**然后**再加密。

**execute 顺序：**

1. 从 `parameterValues` + `pair` 收集 source 明文  
2. `DigestService.computeTargetDigests(...)`（UPDATE WHERE 解析不到 → FAIL）  
3. 对 `appendedParameterIndexes` / 已有 target 列索引 `setString`  
4. 现有加密逻辑；摘要索引不得加密

- [ ] **Step 1: 改 Connection.prepareStatement**

```java
String sqlToPrepare = sql;
DigestRewriteResult digestRewrite = null;
if (EncryptModeHolder.isJdbc()) {
    digestRewrite = maybeRewriteForDigest(sql, dsId);
    if (digestRewrite != null && digestRewrite.isRewritten()) {
        sqlToPrepare = digestRewrite.getSql();
    }
}
PreparedStatement statement = delegate.prepareStatement(sqlToPrepare);
SimpleInterceptorPreparedStatement wrapped =
    new SimpleInterceptorPreparedStatement(statement, sqlToPrepare, dsId);
wrapped.setDigestRewriteResult(digestRewrite);
return wrapped;
```

`maybeRewriteForDigest`：表有 digest → 收集 SQL 中缺失的 target → `tryAppendTargets`。  
至少覆盖常用 `prepareStatement` 重载。

- [ ] **Step 2: DigestWriteSupport + PS 挂钩**

在加密前调用：

```java
DigestWriteSupport.applyDigestsBeforeEncrypt(sql, tables, datasourceId, pair,
    parameterValues, digestRewriteResult, delegate);
```

- [ ] **Step 3: 单测 DigestWriteSupport（给定 pair + parameterValues → target 值）**

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestWriteSupportTest test`  
Expected: PASS

- [ ] **Step 4: 提交**

```bash
git add securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/SimpleInterceptorConnection.java securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/SimpleInterceptorPreparedStatement.java securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestWriteSupport.java securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestWriteSupportTest.java
git commit -m "feat(digest): wire JDBC write path with SQL rewrite and bind"
```

---

### Task 7: JDBC 读路径可选验签

**Files:**
- Create: `securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestReadSupport.java`
- Modify: `securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/ResultSetDecryptingProxy.java`
- Create: `securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestReadSupportTest.java`

**Interfaces:**
- Consumes: `DigestService.verifyRow`
- Produces: `DigestReadSupport.verifyResultRow(tables, ds, Map column→value)`；proxy 在 `next()` 后首次读取时收集本行所需列（解密后）再验签，`next()` 清标志。

- [ ] **Step 1: 单测 DigestReadSupport（FALLBACK / FAIL_FAST）**  
- [ ] **Step 2: 接入 ResultSetDecryptingProxy**  
- [ ] **Step 3: 提交**

```bash
git commit -m "feat(digest): optional verify-on-read for JDBC ResultSet"
```

---

### Task 8: MYBATIS 写 / 读接驳

**Files:**
- Modify: `securt-kit-mybatis/src/main/java/io/github/hexlodev/mybatis/ParameterEncryptHelper.java`
- Modify: `securt-kit-mybatis/src/main/java/io/github/hexlodev/mybatis/ResultDecryptHelper.java`
- Modify: `securt-kit-mybatis/src/main/java/io/github/hexlodev/mybatis/EncryptInterceptor.java`（加密前改 BoundSql）
- Create: `securt-kit-mybatis/src/test/java/io/github/hexlodev/mybatis/DigestParamHelperTest.java`

**Interfaces:**
- Consumes: `DigestService`, `DigestSqlRewriter`, `DigestConfigRegistry`
- Produces:
  - `ParameterEncryptHelper.applyDigests(...)` 在 `encryptParameters` **之前**  
  - 改写 BoundSql SQL；`additionalParameters` 键 `__securtkit_digest_<target>` + 追加 `ParameterMapping`  
  - `ResultDecryptHelper` 解密后 `DigestReadSupport.verifyEntityOrMap`

- [ ] **Step 1: 写 DigestParamHelperTest（INSERT 无 row_digest → SQL 被追加且参数有摘要）**  
- [ ] **Step 2: 实现写路径**  
- [ ] **Step 3: 实现读验签**  
- [ ] **Step 4: 跑测并提交**

Run: `mvn -pl securt-kit-mybatis -am -Dmaven.test.skip=false -Dtest=DigestParamHelperTest test`  
Expected: PASS

```bash
git commit -m "feat(digest): wire MyBatis parameter and result digest hooks"
```

---

### Task 9: Boot2 集成测 + 文档

**Files:**
- Create: `securt-kit-test-boot2` 下 `DigestIntegrityIT`（包路径跟现有 IT 一致）
- Modify: 测试用 yml / schema（增加 `row_digest`；H2 注意 `"user"`）
- Modify: `docs/USAGE.md`
- Modify: `docs/superpowers/specs/2026-09-05-field-digest-integrity-design.md`（状态更新）

**用例：**

1. INSERT 不写 `row_digest` → 落库非空  
2. UPDATE 只改 `phone` + RELOAD → 摘要正确  
3. `verify-on-read=true` 篡改摘要 → FALLBACK 不抛 / FAIL_FAST 抛 `DigestMismatchException`

```bash
mvn -pl securt-kit-test-boot2 -am -Dmaven.test.skip=false -Dtest=DigestIntegrityIT test
```

USAGE 配置示例：

```yaml
securtkit:
  encryptor:
    digest-strategy: io.github.genkidoudou.core.strategy.HmacSha256DigestStrategy
    digest-hmac-key: ${DIGEST_HMAC_KEY}
    digest-partial-update: RELOAD
    digest-verify-on-read: false
    tables:
      - table-name: user
        fields:
          - field-name: phone
        digest:
          - source-fields: [phone]
            target-field: row_digest
```

- [ ] **Step 1: IT 通过**  
- [ ] **Step 2: 文档更新**  
- [ ] **Step 3: 提交**

```bash
git commit -m "test(digest): add boot2 IT and document digest configuration"
```

---

## Self-Review (plan vs spec)

| Spec 项 | Task |
|---------|------|
| 配置 YAML / 覆盖 | 1, 3 |
| 策略扩展 + HMAC | 2 |
| 启动校验 | 3 |
| 单表 SQL 改写 | 4, 6, 8 |
| DigestService 写 + RELOAD/SKIP/FAIL | 5 |
| JDBC 写 | 6 |
| 读可选验签 | 7, 8 |
| MYBATIS 双通道 | 8 |
| 集成测 + USAGE | 9 |
| 非目标 JOIN/BATCH/UI | 未排期（符合） |
| `digest-hmac-key` | Global Constraints + Task 2/3（spec 未写死密钥项，计划补齐） |

**类型一致性：** `PartialUpdate`、`ResolvedDigestRule`、`DigestRewriteResult`、`computeTargetDigests` / `verifyRow` 命名在 Task 1→8 一致。

**占位符扫描：** 无 TBD；RELOAD WHERE 定义为「调用方传入 / 解析不到则 FAIL」，符合第一期边界。

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-05-field-digest-integrity.md`.

**两种执行方式：**

1. **Subagent-Driven（推荐）** — 每 Task 开新子代理，Task 间审查，迭代快  
2. **Inline Execution** — 本会话按 executing-plans 连续做，设检查点  

选哪种？
