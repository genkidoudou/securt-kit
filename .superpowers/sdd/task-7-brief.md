# Task Brief: Task 7

**Work directory:** e:/workspace/luyanan/securt-kit2

## Global Constraints
- Java 8
- verify after decrypt
- ONLY commit Task 7 files
- Test DigestReadSupportTest

## Available
- DigestService.verifyRow
- ResultSetDecryptingProxy (package-private InvocationHandler — may need DigestReadSupport in same package or public helper)

## Task Text From Plan
### Task 7: JDBC 璇昏矾寰勫彲閫夐獙绛?

**Files:**
- Create: `securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestReadSupport.java`
- Modify: `securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/ResultSetDecryptingProxy.java`
- Create: `securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestReadSupportTest.java`

**Interfaces:**
- Consumes: `DigestService.verifyRow`
- Produces: `DigestReadSupport.verifyResultRow(tables, ds, Map column鈫抳alue)`锛沺roxy 鍦?`next()` 鍚庨娆¤鍙栨椂鏀堕泦鏈鎵€闇€鍒楋紙瑙ｅ瘑鍚庯級鍐嶉獙绛撅紝`next()` 娓呮爣蹇椼€?

- [ ] **Step 1: 鍗曟祴 DigestReadSupport锛團ALLBACK / FAIL_FAST锛?*  
- [ ] **Step 2: 鎺ュ叆 ResultSetDecryptingProxy**  
- [ ] **Step 3: 鎻愪氦**

```bash
git commit -m "feat(digest): optional verify-on-read for JDBC ResultSet"
```

---


