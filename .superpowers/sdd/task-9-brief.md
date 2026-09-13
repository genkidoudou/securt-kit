# Task Brief: Task 9

**Work directory:** e:/workspace/luyanan/securt-kit2

## Global Constraints
- Java 8 / Boot2
- H2 reserved word: quote "user" if needed
- Add row_digest column to schema used by IT
- Document digest-hmac-key in USAGE
- ONLY commit Task 9 related test/doc/schema/yml files (+ schema SQL)
- Test: mvn -pl securt-kit-test-boot2 -am "-Dmaven.test.skip=false" "-Dtest=DigestIntegrityIT" "-Dsurefire.failIfNoSpecifiedTests=false" test

## Task Text From Plan
### Task 9: Boot2 闆嗘垚娴?+ 鏂囨。

**Files:**
- Create: `securt-kit-test-boot2` 涓?`DigestIntegrityIT`锛堝寘璺緞璺熺幇鏈?IT 涓€鑷达級
- Modify: 娴嬭瘯鐢?yml / schema锛堝鍔?`row_digest`锛汬2 娉ㄦ剰 `"user"`锛?
- Modify: `docs/USAGE.md`
- Modify: `docs/superpowers/specs/2026-09-05-field-digest-integrity-design.md`锛堢姸鎬佹洿鏂帮級

**鐢ㄤ緥锛?*

1. INSERT 涓嶅啓 `row_digest` 鈫?钀藉簱闈炵┖  
2. UPDATE 鍙敼 `phone` + RELOAD 鈫?鎽樿姝ｇ‘  
3. `verify-on-read=true` 绡℃敼鎽樿 鈫?FALLBACK 涓嶆姏 / FAIL_FAST 鎶?`DigestMismatchException`

```bash
mvn -pl securt-kit-test-boot2 -am -Dmaven.test.skip=false -Dtest=DigestIntegrityIT test
```

USAGE 閰嶇疆绀轰緥锛?

```yaml
securtkit:
  encryptor:
    digest-strategy: io.github.hexlodev.core.strategy.HmacSha256DigestStrategy
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

- [ ] **Step 1: IT 閫氳繃**  
- [ ] **Step 2: 鏂囨。鏇存柊**  
- [ ] **Step 3: 鎻愪氦**

```bash
git commit -m "test(digest): add boot2 IT and document digest configuration"
```

---


