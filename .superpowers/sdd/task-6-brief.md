# Task Brief: Task 6

**Work directory:** e:/workspace/luyanan/securt-kit2

## Global Constraints
- Java 8
- digest BEFORE encrypt
- Rewrite SQL BEFORE delegate.prepareStatement
- Digest column must NOT be encrypted
- Single-row VALUES only for appended indexes (multi-row indexes known buggy — document or skip multi-row)
- ONLY commit Task 6 files listed below
- Dirty tree: Connection/PreparedStatement already have WIP — ADD digest hooks carefully; stage only intentional Task 6 hunks if possible

## Available
- DigestSqlRewriter, DigestRewriteResult, DigestService, DigestConfigRegistry
- EncryptModeHolder.isJdbc()
- SimpleInterceptorPreparedStatement has parameterValues LinkedHashMap
- SqlParseCache / SecurtkitUtils for table parse

## Files to create/modify
- SimpleInterceptorConnection.java
- SimpleInterceptorPreparedStatement.java  
- DigestWriteSupport.java (create)
- DigestWriteSupportTest.java (create)

## Task Text From Plan
### Task 6: JDBC 鍐欒矾寰勶紙prepare 鍓嶆敼鍐?+ execute 鍓嶆敞鍏ワ級

**Files:**
- Modify: `securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/SimpleInterceptorConnection.java`
- Modify: `securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/SimpleInterceptorPreparedStatement.java`
- Create: `securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestWriteSupport.java`
- Create: `securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestWriteSupportTest.java`

**Interfaces:**
- Consumes: `DigestSqlRewriter`, `DigestService`, `DigestConfigRegistry`
- Produces: JDBC 妯″紡涓?prepare **涔嬪墠**鏀瑰啓 SQL锛汸S 鎸佹湁 `DigestRewriteResult`锛沞xecute 鍓嶆敞鍏ユ憳瑕侊紝**鐒跺悗**鍐嶅姞瀵嗐€?

**execute 椤哄簭锛?*

1. 浠?`parameterValues` + `pair` 鏀堕泦 source 鏄庢枃  
2. `DigestService.computeTargetDigests(...)`锛圲PDATE WHERE 瑙ｆ瀽涓嶅埌 鈫?FAIL锛? 
3. 瀵?`appendedParameterIndexes` / 宸叉湁 target 鍒楃储寮?`setString`  
4. 鐜版湁鍔犲瘑閫昏緫锛涙憳瑕佺储寮曚笉寰楀姞瀵?

- [ ] **Step 1: 鏀?Connection.prepareStatement**

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

`maybeRewriteForDigest`锛氳〃鏈?digest 鈫?鏀堕泦 SQL 涓己澶辩殑 target 鈫?`tryAppendTargets`銆? 
鑷冲皯瑕嗙洊甯哥敤 `prepareStatement` 閲嶈浇銆?

- [ ] **Step 2: DigestWriteSupport + PS 鎸傞挬**

鍦ㄥ姞瀵嗗墠璋冪敤锛?

```java
DigestWriteSupport.applyDigestsBeforeEncrypt(sql, tables, datasourceId, pair,
    parameterValues, digestRewriteResult, delegate);
```

- [ ] **Step 3: 鍗曟祴 DigestWriteSupport锛堢粰瀹?pair + parameterValues 鈫?target 鍊硷級**

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestWriteSupportTest test`  
Expected: PASS

- [ ] **Step 4: 鎻愪氦**

```bash
git add securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/SimpleInterceptorConnection.java securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/SimpleInterceptorPreparedStatement.java securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestWriteSupport.java securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestWriteSupportTest.java
git commit -m "feat(digest): wire JDBC write path with SQL rewrite and bind"
```

---


