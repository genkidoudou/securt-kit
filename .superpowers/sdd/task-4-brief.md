# Task Brief: Task 4

**Work directory:** e:/workspace/luyanan/securt-kit2

## Global Constraints
- Java 8
- Single-table INSERT/UPDATE rewrite only; multi-table → rewritten=false + warnMessage
- ONLY commit Task 4 files
- Test: mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestSqlRewriterTest test
- Use net.sf.jsqlparser (same as rest of core sources before shade)

## Task Text From Plan
### Task 4: DigestSqlRewriter锛堢函鍑芥暟锛?

**Files:**
- Create: `securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestRewriteResult.java`
- Create: `securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestSqlRewriter.java`
- Create: `securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestSqlRewriterTest.java`

**Interfaces:**
- Consumes: jsqlparser
- Produces:
  - `DigestRewriteResult { String sql; List<Integer> appendedParameterIndexes; boolean rewritten; String warnMessage; }`
  - `DigestSqlRewriter.tryAppendTargets(String sql, List<String> missingTargetFields)`  
    - 浠呭崟琛?Insert / Update  
    - INSERT锛氬垪鏈熬鍔犲垪锛孷ALUES 鍔?`?`  
    - UPDATE锛歋ET 鏈熬鍔?`col=?`锛堢储寮?= 鍘?SET 鍗犱綅绗︽暟 + 1锛? 
    - 宸叉湁 target锛氫笉閲嶅杩藉姞  
    - 澶氳〃 / 瑙ｆ瀽澶辫触锛歚rewritten=false` + warnMessage

- [ ] **Step 1: 鍐欏け璐ユ祴璇?*

```java
package io.github.hexlodev.core.digest;

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
        // 鏂?? 鍦?SET 鏈熬銆乄HERE 涔嬪墠 鈫?绱㈠紩 2锛沇HERE id 椤哄欢涓?3
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

- [ ] **Step 2: 璺戞祴纭澶辫触**

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestSqlRewriterTest test`  
Expected: FAIL

- [ ] **Step 3: 瀹炵幇 Rewriter**

浣跨敤 `CCJSqlParserUtil.parse`锛?

- `Insert`锛氬崟琛ㄣ€佹湁鏄惧紡鍒楋紱杩藉姞 `Column` + 鍚?`ExpressionList` 杩藉姞 `JdbcParameter`  
- `Update`锛氬崟琛ㄦ棤 join锛汼ET 杩藉姞鍒?`JdbcParameter`  
- 鎸?SQL 涓?`?` 鍑虹幇椤哄簭璁＄畻 1-based 杩藉姞绱㈠紩  

- [ ] **Step 4: 璺戞祴閫氳繃骞舵彁浜?*

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestSqlRewriterTest test`  
Expected: PASS锛堣嫢绱㈠紩涓庡疄鐜颁笉涓€鑷达紝浠ュ疄鐜颁负鍑嗕慨姝ｆ柇瑷€锛?

```bash
git add securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestSqlRewriter.java securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestRewriteResult.java securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestSqlRewriterTest.java
git commit -m "feat(digest): add single-table SQL rewriter for digest columns"
```

---


