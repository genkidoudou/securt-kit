# Task Brief: Task 1

**Plan:** docs/superpowers/plans/2026-09-05-field-digest-integrity.md
**Work directory:** e:/workspace/luyanan/securt-kit2

## Global Constraints (binding)

- Java 8 only; no var/records/List.of
- Default profile has maven.test.skip=true; tests need -Dmaven.test.skip=false
- HMAC key config item digest-hmac-key is for later tasks; Task 1 only adds the property field

## Task Text From Plan
### Task 1: 閰嶇疆妯″瀷涓庢灇涓?

**Files:**
- Modify: `securt-kit-core/src/main/java/io/github/hexlodev/core/config/FieldEncryptorProperties.java`
- Create: `securt-kit-core/src/test/java/io/github/hexlodev/core/config/DigestConfigModelTest.java`
- Modify: `securt-kit-core/pom.xml`锛堣ˉ junit-jupiter test 渚濊禆 + surefire锛?

**Interfaces:**
- Consumes: 鏃?
- Produces:
  - `FieldEncryptorProperties.PartialUpdate`锛歚SKIP`, `RELOAD`, `FAIL`
  - `FieldEncryptorProperties.DigestConfig`锛歚List<String> sourceFields`, `String targetField`, `String strategy`, `PartialUpdate partialUpdate`, `Boolean verifyOnRead`, `FailurePolicy failurePolicy`
  - 鍏ㄥ眬瀛楁锛歚String digestStrategy`, `PartialUpdate digestPartialUpdate`锛堥粯璁?RELOAD锛? `boolean digestVerifyOnRead`锛堥粯璁?false锛? `FailurePolicy digestFailurePolicy`锛堝彲 null锛? `String digestHmacKey`, `TableConfig.digest`锛歚List<DigestConfig>`

- [ ] **Step 1: 涓?core 澧炲姞娴嬭瘯渚濊禆**

鍦?`securt-kit-core/pom.xml` 鐨?`</dependencies>` 鍓嶅姞鍏ワ細

```xml
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
        </dependency>
```

鍦?`<build><plugins>` 涓姞鍏ワ細

```xml
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
```

- [ ] **Step 2: 鍐欏け璐ユ祴璇曪紙榛樿鍊硷級**

```java
package io.github.hexlodev.core.config;

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

- [ ] **Step 3: 璺戞祴纭澶辫触锛堢己瀛楁锛?*

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestConfigModelTest test`

Expected: 缂栬瘧澶辫触鎴栨柇瑷€澶辫触锛堝皻鏃?`digestPartialUpdate` 绛夊瓧娈碉級

- [ ] **Step 4: 瀹炵幇閰嶇疆瀛楁**

鍦?`FieldEncryptorProperties` 涓?`failurePolicy` 闄勮繎澧炲姞锛?

```java
    private String digestStrategy;
    private PartialUpdate digestPartialUpdate = PartialUpdate.RELOAD;
    private boolean digestVerifyOnRead = false;
    private FailurePolicy digestFailurePolicy;
    private String digestHmacKey;
```

鍦?`TableConfig` 涓?`fields` 鍚庡鍔狅細

```java
        private List<DigestConfig> digest;
```

鏂板鍐呴儴绫讳笌鏋氫妇锛?

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

锛堣ˉ `import java.util.Locale;`锛?

- [ ] **Step 5: 璺戞祴閫氳繃骞舵彁浜?*

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestConfigModelTest test`  
Expected: PASS

```bash
git add securt-kit-core/pom.xml securt-kit-core/src/main/java/io/github/hexlodev/core/config/FieldEncryptorProperties.java securt-kit-core/src/test/java/io/github/hexlodev/core/config/DigestConfigModelTest.java
git commit -m "feat(digest): add digest config model and defaults"
```

---


