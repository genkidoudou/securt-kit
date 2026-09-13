# Task 2 Report: 策略接口扩展 + HMAC 实现

**Date:** 2026-09-05  
**Status:** ✅ Complete  
**Depends on:** Task 1 (commit a105cdc — digest config model)

## Summary

Extended `FieldEncryptorStrategy` with default digest methods and implemented `HmacSha256DigestStrategy` for field integrity HMAC-SHA256 digests.

## TDD Execution

| Step | Action | Result |
|------|--------|--------|
| 1 | Created `HmacSha256DigestStrategyTest` with 2 tests | Done |
| 2 | Ran tests before implementation | **FAIL** — `HmacSha256DigestStrategy` class not found (compilation error) |
| 3 | Added default methods to `FieldEncryptorStrategy` | Done |
| 4 | Implemented `HmacSha256DigestStrategy` | Done |
| 5 | Ran tests after implementation | **PASS** — 2 tests, 0 failures |

### Test Command

```bash
mvn -pl securt-kit-core -am "-Dmaven.test.skip=false" "-Dtest=HmacSha256DigestStrategyTest" test
```

**Note (PowerShell):** Quote `-D` properties to avoid parsing issues.

## Files Changed

| File | Change |
|------|--------|
| `securt-kit-core/.../FieldEncryptorStrategy.java` | Added `supportsDigest()`, `digest()`, `verifyDigest()` defaults at end of interface |
| `securt-kit-core/.../HmacSha256DigestStrategy.java` | **New** — digest-only HMAC-SHA256 strategy |
| `securt-kit-core/.../HmacSha256DigestStrategyTest.java` | **New** — deterministic/verify + order-matters tests |

## Implementation Details

### FieldEncryptorStrategy defaults

- `supportsDigest()` → `false` (existing encrypt strategies unchanged)
- `digest()` → `null`
- `verifyDigest()` — constant-time string compare via `MessageDigest.isEqual` on UTF-8 bytes; returns `false` for null inputs or when `digest()` returns null

### HmacSha256DigestStrategy

- Constructor: `HmacSha256DigestStrategy(String key)` — rejects null/blank key
- `supportsDigest()` → `true`
- `encryption()` / `decryption()` → `UnsupportedOperationException` (digest-only)
- Canonical form: iterate map entries in iteration order, format `key=value\n` (null values as empty string), HMAC-SHA256, Base64 encode
- **Order matters:** `LinkedHashMap` insertion order affects digest (tested)

## Commit

```
feat(digest): extend FieldEncryptorStrategy with HMAC digest
```

Only the three Task 2 files were staged; other dirty tree files left untouched.

## Test Results

```
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- `digestIsDeterministicAndVerifies` — same input → same digest; verify passes; changed value → verify fails
- `orderMatters` — different key order → different digest

## Out of Scope (Task 3+)

- `StrategyCache` has no-arg default constructor; HMAC strategy must be registered manually in `ConfigInitializer` with `digestHmacKey` via `StrategyCache.registerStrategy(...)` — not done in this task
- No integration with SQL interceptor or digest column persistence yet

## Concerns / Notes

1. **Map iteration order:** Callers must use a stable order (e.g. `LinkedHashMap` sorted by field config order) when building `sourcePlainValues`; otherwise digests will not match across writes.
2. **Key management:** `digestHmacKey` from Task 1 config is not wired until Task 3.
3. **Blank key:** Constructor throws `IllegalArgumentException` — ConfigInitializer should validate before instantiation.
