# Task Brief: Task 3

**Work directory:** e:/workspace/luyanan/securt-kit2

## Global Constraints
- Java 8 only
- digest-hmac-key required when using HmacSha256DigestStrategy
- ONLY commit Task 3 files; dirty tree has unrelated changes — do not stage them
- Tests: mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestConfigRegistryTest test

## Already available from prior tasks
- FieldEncryptorProperties DigestConfig / digestHmacKey / PartialUpdate (a105cdc)
- FieldEncryptorStrategy.supportsDigest/digest/verifyDigest + HmacSha256DigestStrategy (c6d3246)
- StrategyCache.registerStrategy / getStrategy / clear exist
- ConfigInitializer.initialize / reset / validateConfiguration exist — extend carefully
- TableCache.reset may or may not exist; prefer ConfigInitializer.reset + DigestConfigRegistry.clear in tests

## Task Text From Plan
### Task 3: DigestConfigRegistry + 鍚姩鏍￠獙

**Files:**
- Create: `securt-kit-core/src/main/java/io/github/hexlodev/core/digest/ResolvedDigestRule.java`
- Create: `securt-kit-core/src/main/java/io/github/hexlodev/core/config/DigestConfigRegistry.java`
- Modify: `securt-kit-core/src/main/java/io/github/hexlodev/core/config/ConfigInitializer.java`
- Create: `securt-kit-core/src/test/java/io/github/hexlodev/core/config/DigestConfigRegistryTest.java`

**Interfaces:**
- Consumes: Task 1/2 绫诲瀷涓?`StrategyCache`
- Produces:
  - `ResolvedDigestRule`锛歵ableName銆乻ourceFields銆乼argetField銆乻trategyClass銆乸artialUpdate銆乿erifyOnRead銆乫ailurePolicy
  - `DigestConfigRegistry.register(String datasourceId, String table, List<ResolvedDigestRule>)`
  - `DigestConfigRegistry.getRules(String table, String datasourceId)` 鈫?`List<ResolvedDigestRule>`
  - `DigestConfigRegistry.clear()`
  - `ConfigInitializer` 鍦ㄦ牎楠?娉ㄥ唽闃舵鍐欏叆 registry锛沗reset` 鏃?`clear()`

- [ ] **Step 1: 鍐欏け璐ユ祴璇?*

```java
package io.github.hexlodev.core.config;

import io.github.hexlodev.core.cache.StrategyCache;
import io.github.hexlodev.core.strategy.HmacSha256DigestStrategy;
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

- [ ] **Step 2: 璺戞祴纭澶辫触**

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestConfigRegistryTest test`  
Expected: FAIL锛堢己 registry / 鏍￠獙锛?

- [ ] **Step 3: 瀹炵幇 ResolvedDigestRule + Registry**

`ResolvedDigestRule` 瀛楁锛歚tableName`, `sourceFields`, `targetField`, `strategyClass`, `partialUpdate`, `verifyOnRead`, `failurePolicy`锛堜笉鍙彉 + getters锛夈€?

`DigestConfigRegistry`锛歚datasourceId 鈫?tableName(lower) 鈫?List<ResolvedDigestRule>`锛涙彁渚?`register` / `getRules` / `clear` / `hasDigest(table, ds)`銆?

- [ ] **Step 4: 鍦?ConfigInitializer 涓牎楠屽苟娉ㄥ唽**

1. `sourceFields` 闈炵┖锛沗targetField` 闈炵┖  
2. 鍚岃〃 `targetField` 涓嶉噸澶嶏紙ignore case锛? 
3. `targetField` 涓嶅湪鍚岃〃鍔犲瘑 fields 涓? 
4. strategy锛氶」绾?鈫?鍏ㄥ眬 `digestStrategy`锛涗负绌哄け璐? 
5. 鑻ヤ负 `HmacSha256DigestStrategy`锛歚digestHmacKey` 闈炵┖骞?`registerStrategy`  
6. `supportsDigest()` 蹇呴』涓?true  
7. 鍚堝苟 partialUpdate / verifyOnRead / failurePolicy锛堥」 鈫?鍏ㄥ眬 鈫?榛樿锛沠ailurePolicy 鏈€缁堝洖钀藉埌 `failurePolicy`锛?

`reset()` 鏈熬璋冪敤 `DigestConfigRegistry.clear()`銆?

- [ ] **Step 5: 璺戞祴閫氳繃骞舵彁浜?*

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestConfigRegistryTest test`  
Expected: PASS

```bash
git add securt-kit-core/src/main/java/io/github/hexlodev/core/digest/ResolvedDigestRule.java securt-kit-core/src/main/java/io/github/hexlodev/core/config/DigestConfigRegistry.java securt-kit-core/src/main/java/io/github/hexlodev/core/config/ConfigInitializer.java securt-kit-core/src/test/java/io/github/hexlodev/core/config/DigestConfigRegistryTest.java
git commit -m "feat(digest): register and validate digest configs at startup"
```

---


