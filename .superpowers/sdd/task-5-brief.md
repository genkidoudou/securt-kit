# Task Brief: Task 5

**Work directory:** e:/workspace/luyanan/securt-kit2

## Global Constraints
- Java 8
- Plaintext digest input; LinkedHashMap order by sourceFields
- INSERT + missing sources + RELOAD => fail with message containing INSERT cannot RELOAD
- ONLY commit Task 5 files
- Test: mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestServiceTest test

## Available APIs
- DigestConfigRegistry.getRules / register / clear
- ResolvedDigestRule getters
- StrategyCache + HmacSha256DigestStrategy
- FieldCryptoService / FieldCryptoServiceHolder for decrypt on reload (mock in tests)
- SecurtKitException hierarchy — check existing exception package

## Task Text From Plan
### Task 5: DigestService锛堣绠?/ partial-update / 楠岀锛?

**Files:**
- Create: `securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestService.java`
- Create: `securt-kit-core/src/main/java/io/github/hexlodev/core/exception/DigestMismatchException.java`
- Create: `securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestServiceTest.java`

**Interfaces:**
- Consumes: `DigestConfigRegistry`, `StrategyCache`, `FieldCryptoService`锛堣В瀵嗚ˉ璇诲垪锛? `ResolvedDigestRule`
- Produces:
  - `Map<String, String> computeTargetDigests(String table, String datasourceId, Map<String, String> availablePlainByColumn, boolean insert, Connection connForReload, String reloadWhereSql, List<Object> reloadWhereParams)`  
    鈫?key=`targetField`锛堝缓璁?lower锛夛紝value=digest锛汼KIP 鏃剁渷鐣? 
  - `void verifyRow(String table, String datasourceId, Map<String, String> plainByColumn, Map<String, String> digestByTarget)`  

**RELOAD锛?* 璋冪敤鏂逛紶鍏?WHERE锛涗负绌哄垯 FAIL銆係QL锛歚SELECT missing_cols FROM table WHERE ...`锛屽姞瀵嗗垪缁?`FieldCryptoService.decrypt`銆侷NSERT + 缂哄瓧娈?+ RELOAD 鈫?鎶涢敊锛屾秷鎭惈 `INSERT cannot RELOAD`銆?

- [ ] **Step 1: 鍐欏け璐ユ祴璇曪紙绾唴瀛橈級**

```java
@Test
void skipWhenPartial() {
    // rule: sources=[phone,id_card], partial=SKIP锛涗粎 phone 鈫?杈撳嚭绌?
}

@Test
void failWhenPartialAndFailPolicy() {
    // partial=FAIL 鈫?throws
}

@Test
void verifyMismatchFallbackDoesNotThrow() {
    // verifyOnRead=true, FALLBACK, 閿欒 digest 鈫?涓嶆姏
}

@Test
void verifyMismatchFailFastThrows() {
    // FAIL_FAST 鈫?DigestMismatchException
}
```

`@BeforeEach` 鍚?`DigestConfigRegistry` 娉ㄥ唽 rule + HMAC銆?

- [ ] **Step 2: 瀹炵幇 DigestService**

鏈夊簭 Map锛?

```java
LinkedHashMap<String, String> ordered = new LinkedHashMap<String, String>();
for (String col : rule.getSourceFields()) {
    // ignore-case 浠?available 鍙栧€?
}
String digest = StrategyCache.getStrategy(rule.getStrategyClass()).digest(ordered);
```

楠岀锛歚verifyOnRead` 鍏抽棴鍒欒繑鍥烇紱digest null 鈫?warn锛涚己婧?鈫?debug锛涘け璐ユ寜 `FALLBACK`/`FAIL_FAST`/`SKIP`锛坄RETRY` 褰?FALLBACK锛夈€?

`DigestMismatchException` 缁ф壙椤圭洰鏃㈡湁 `SecurtKitException`锛堣嫢鏃犲垯 `RuntimeException`锛夈€?

- [ ] **Step 3: 璺戞祴閫氳繃骞舵彁浜?*

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestServiceTest test`  
Expected: PASS

```bash
git add securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestService.java securt-kit-core/src/main/java/io/github/hexlodev/core/exception/DigestMismatchException.java securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestServiceTest.java
git commit -m "feat(digest): add DigestService for compute and verify"
```

---


