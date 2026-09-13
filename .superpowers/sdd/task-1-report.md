# Task 1 Report: 配置模型与枚举

**Status:** DONE  
**Commit:** `a105cdc` — feat(digest): add digest config model and defaults  
**Date:** 2026-09-05

## Summary

Added digest configuration model to `FieldEncryptorProperties`: global digest fields, `PartialUpdate` enum, `DigestConfig` inner class, and `TableConfig.digest` list. Added JUnit 5 test dependency and Surefire plugin to `securt-kit-core`.

## TDD Evidence

### RED (Step 3)

Command:
```bash
mvn -pl securt-kit-core -am "-Dmaven.test.skip=false" "-Dtest=DigestConfigModelTest" test
```

Result: **BUILD FAILURE** — test compilation failed with 8 errors:
- `PartialUpdate` not found
- `getDigestPartialUpdate()`, `isDigestVerifyOnRead()`, `getDigestFailurePolicy()` not found on `FieldEncryptorProperties`
- `DigestConfig` not found
- `getDigest()` / `setDigest()` not found on `TableConfig`

### GREEN (Step 5)

Same command after implementation.

Result: **BUILD SUCCESS**
```
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

Tests:
- `globalDigestDefaults` — asserts `digestPartialUpdate=RELOAD`, `digestVerifyOnRead=false`, `digestFailurePolicy=null`
- `tableCanHoldDigestList` — asserts `TableConfig` can hold a `List<DigestConfig>` with source/target fields

## Files Changed

| File | Action |
|------|--------|
| `securt-kit-core/pom.xml` | Added `junit-jupiter` 5.10.2 (test scope) and `maven-surefire-plugin` 3.2.5 |
| `securt-kit-core/src/main/java/io/github/hexlodev/core/config/FieldEncryptorProperties.java` | Added global digest fields, `DigestConfig`, `PartialUpdate`, `TableConfig.digest` |
| `securt-kit-core/src/test/java/io/github/hexlodev/core/config/DigestConfigModelTest.java` | Created (2 tests) |

## Implementation Details

### Global fields (after `failurePolicy`)
- `digestStrategy` (String, nullable)
- `digestPartialUpdate` (PartialUpdate, default `RELOAD`)
- `digestVerifyOnRead` (boolean, default `false`)
- `digestFailurePolicy` (FailurePolicy, nullable)
- `digestHmacKey` (String, for later tasks)

### New types
- **`PartialUpdate` enum:** `SKIP`, `RELOAD`, `FAIL` with `fromString()` defaulting to `RELOAD` (uses `Locale.ROOT`)
- **`DigestConfig` class:** `sourceFields`, `targetField`, `strategy`, `partialUpdate`, `verifyOnRead`, `failurePolicy`

### TableConfig
- Added `private List<DigestConfig> digest` after `fields`

### Preserved existing content
- `Mode` enum, `likePatternHandler`, `ignoreTableCase`, and all other existing fields/classes unchanged.

## Self-Review

| Check | Result |
|-------|--------|
| Java 8 compatible (no var/records/List.of) | ✅ |
| Only Task 1 files staged/committed | ✅ (3 files, 137 insertions) |
| Existing WIP (Mode, likePatternHandler, etc.) preserved | ✅ |
| Default values match brief | ✅ |
| `PartialUpdate.fromString` uses `Locale.ROOT` | ✅ |
| `digestHmacKey` field added (config only, no logic) | ✅ |
| Commit message matches brief | ✅ |

### Notes
- PowerShell requires quoting Maven `-D` properties: `"-Dmaven.test.skip=false"`.
- No tests added for `PartialUpdate.fromString()` in this task (brief only specified default-value tests).

## Concerns

None.
