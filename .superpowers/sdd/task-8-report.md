# Task 8 Report: MYBATIS 写 / 读接驳

## Status

完成。

## Changes

- `ParameterEncryptHelper.applyDigests(...)` 在字段加密前基于明文计算摘要。
- 使用 `DigestSqlRewriter` 原地改写 `BoundSql.sql`，新增
  `__securtkit_digest_<target>` additional parameter 与对应 `ParameterMapping`。
- UPDATE RELOAD 使用当前 MyBatis 事务连接，并按原 SQL 的 WHERE 参数构造补读条件。
- `EncryptInterceptor` 在 `encryptParameters` 前调用摘要写接驳；改写后重新解析 SQL，
  非 6 参数调用包装同一 `BoundSql`。
- `ResultDecryptHelper` 在解密实体、Map 或集合后调用 `DigestReadSupport.verifyResultRow`。
- 新增 `DigestParamHelperTest`，覆盖 INSERT 摘要追加及 Map 解密后 FAIL_FAST 验签。

## Verification

- PASS:
  `mvn -pl securt-kit-mybatis -am -Dmaven.test.skip=false -Dtest=DigestParamHelperTest -Dsurefire.failIfNoSpecifiedTests=false test`
  （2 tests, 0 failures, 0 errors）
- IDE lints: 0 errors。
- 任务给出的原命令缺少 `-Dsurefire.failIfNoSpecifiedTests=false`，会在上游
  `securt-kit-core` 因找不到 `DigestParamHelperTest` 提前失败。
- MyBatis 全量测试中的既有 `MpWrapperParamEncryptTest` 独立运行也有 4 个失败；
  与本任务定向测试无关，未在本任务范围内修改。

## Concerns

- 多行 INSERT 摘要改写按 core 现有约束跳过并记录警告。
- 本提交仅包含任务指定的四个 `securt-kit-mybatis` 文件；本报告不纳入提交。

## Important Finding Fix

- `ParameterEncryptHelper.applyDigests(...)` 现在先计算摘要，再仅将实际计算出的
  target 传给 `DigestSqlRewriter`，避免 `partialUpdate=SKIP` 产生未绑定占位符。
- 新增双摘要规则的部分 UPDATE 回归测试，确认 SQL 占位符数与
  `ParameterMapping` 数一致，且跳过的摘要 target 不会写入 SQL。
- PASS:
  `mvn -pl securt-kit-mybatis -am "-Dmaven.test.skip=false" "-Dtest=DigestParamHelperTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  （3 tests, 0 failures, 0 errors）。
