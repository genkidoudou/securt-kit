# Task Brief: Task 2

**Work directory:** e:/workspace/luyanan/securt-kit2

## Global Constraints
- Java 8 only
- Tests: mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=HmacSha256DigestStrategyTest test
- ONLY commit Task 2 files listed in the plan (do not stage unrelated dirty files)

## From Task 1 (already done)
- FieldEncryptorProperties has digestHmacKey and digest config model (commit a105cdc)

## Task Text From Plan
### Task 2: 绛栫暐鎺ュ彛鎵╁睍 + HMAC 瀹炵幇

**Files:**
- Modify: `securt-kit-core/src/main/java/io/github/hexlodev/core/strategy/FieldEncryptorStrategy.java`
- Create: `securt-kit-core/src/main/java/io/github/hexlodev/core/strategy/HmacSha256DigestStrategy.java`
- Create: `securt-kit-core/src/test/java/io/github/hexlodev/core/strategy/HmacSha256DigestStrategyTest.java`

**Interfaces:**
- Consumes: Task 1 閰嶇疆涓殑 `digestHmacKey`锛堟瀯閫犳敞鍏ワ級
- Produces:
  - `default boolean supportsDigest()` 鈫?`false`
  - `default String digest(Map<String, String> sourcePlainValues)` 鈫?`null`
  - `default boolean verifyDigest(Map<String, String> sourcePlainValues, String digestValue)`
  - `HmacSha256DigestStrategy(String key)`锛歚supportsDigest()==true`锛沗encryption`/`decryption` 鎶?`UnsupportedOperationException`锛堜粎浣?digest 绛栫暐锛?

- [ ] **Step 1: 鍐欏け璐ユ祴璇?*

```java
package io.github.hexlodev.core.strategy;

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

- [ ] **Step 2: 璺戞祴纭澶辫触**

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=HmacSha256DigestStrategyTest test`  
Expected: 鎵句笉鍒扮被 / 缂栬瘧澶辫触

- [ ] **Step 3: 鎵╁睍鎺ュ彛**

鍦?`FieldEncryptorStrategy` 鏈熬锛坄decryption` 涔嬪悗锛夊鍔狅細

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

- [ ] **Step 4: 瀹炵幇 HMAC 绛栫暐**

```java
package io.github.hexlodev.core.strategy;

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

娉ㄦ剰锛歚StrategyCache` 榛樿鏃犲弬鏋勯€狅紱HMAC 绛栫暐闇€鍦?`ConfigInitializer`锛圱ask 3锛夐噷鐢?`digestHmacKey` **鎵嬪姩** `StrategyCache.registerStrategy(...)`锛屼笉瑕佷緷璧栧弽灏勬棤鍙?new銆?

- [ ] **Step 5: 璺戞祴閫氳繃骞舵彁浜?*

Run: `mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=HmacSha256DigestStrategyTest test`  
Expected: PASS

```bash
git add securt-kit-core/src/main/java/io/github/hexlodev/core/strategy/FieldEncryptorStrategy.java securt-kit-core/src/main/java/io/github/hexlodev/core/strategy/HmacSha256DigestStrategy.java securt-kit-core/src/test/java/io/github/hexlodev/core/strategy/HmacSha256DigestStrategyTest.java
git commit -m "feat(digest): extend FieldEncryptorStrategy with HMAC digest"
```

---


