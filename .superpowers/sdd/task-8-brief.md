# Task Brief: Task 8

**Work directory:** e:/workspace/luyanan/securt-kit2

## Global Constraints
- Java 8
- applyDigests BEFORE encryptParameters
- Use DigestSqlRewriter / DigestService / DigestReadSupport from core
- ONLY commit mybatis module files listed
- Test: mvn -pl securt-kit-mybatis -am -Dmaven.test.skip=false -Dtest=DigestParamHelperTest test

## Patterns
- Follow ParameterEncryptHelper / MpWrapperParamEncryptTest style
- BoundSql rewrite via MetaObject; additionalParameters key __securtkit_digest_<target>

## Task Text From Plan
### Task 8: MYBATIS 鍐?/ 璇绘帴椹?

**Files:**
- Modify: `securt-kit-mybatis/src/main/java/io/github/hexlodev/mybatis/ParameterEncryptHelper.java`
- Modify: `securt-kit-mybatis/src/main/java/io/github/hexlodev/mybatis/ResultDecryptHelper.java`
- Modify: `securt-kit-mybatis/src/main/java/io/github/hexlodev/mybatis/EncryptInterceptor.java`锛堝姞瀵嗗墠鏀?BoundSql锛?
- Create: `securt-kit-mybatis/src/test/java/io/github/hexlodev/mybatis/DigestParamHelperTest.java`

**Interfaces:**
- Consumes: `DigestService`, `DigestSqlRewriter`, `DigestConfigRegistry`
- Produces:
  - `ParameterEncryptHelper.applyDigests(...)` 鍦?`encryptParameters` **涔嬪墠**  
  - 鏀瑰啓 BoundSql SQL锛沗additionalParameters` 閿?`__securtkit_digest_<target>` + 杩藉姞 `ParameterMapping`  
  - `ResultDecryptHelper` 瑙ｅ瘑鍚?`DigestReadSupport.verifyEntityOrMap`

- [ ] **Step 1: 鍐?DigestParamHelperTest锛圛NSERT 鏃?row_digest 鈫?SQL 琚拷鍔犱笖鍙傛暟鏈夋憳瑕侊級**  
- [ ] **Step 2: 瀹炵幇鍐欒矾寰?*  
- [ ] **Step 3: 瀹炵幇璇婚獙绛?*  
- [ ] **Step 4: 璺戞祴骞舵彁浜?*

Run: `mvn -pl securt-kit-mybatis -am -Dmaven.test.skip=false -Dtest=DigestParamHelperTest test`  
Expected: PASS

```bash
git commit -m "feat(digest): wire MyBatis parameter and result digest hooks"
```

---


