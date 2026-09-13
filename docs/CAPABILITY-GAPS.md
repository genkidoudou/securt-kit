# 能力补强说明（A 类高价值项）

> 更新日期：2026-09-05

相对「仅等值 + 裸列」主路径，已补强下列能力。**范围比较 / 函数包裹在业务语义上仍可能不正确**（密文无序、`UPPER(密文)` ≠ `encrypt(UPPER(明文))`），但参数侧不再漏加密。

## 已实现

| 项 | 行为 |
|----|------|
| `>` / `<` / `>=` / `<=` | 与 `=` 相同建立 `?`→列映射并加密参数 |
| `BETWEEN ? AND ?` | 两个边界参数均可映射加密 |
| `NOT (col = ?)` | 递归进入取反表达式 |
| `UPPER(col) = ?` 等 | 从函数参数提取首个列再映射；`SET col = UPPER(?)` 原已可映射 |
| `ON DUPLICATE KEY UPDATE col=?` | 解析 duplicate UpdateSet |
| `INSERT ... SET col=?` | 支持；**无列名 VALUES** 仍不猜列序（仅 warn） |
| `UPDATE ... JOIN` | 同时收集 `startJoins` 与 `joins` |
| `setNString` / `setObject(..., scale)` | 走与 `setString` 相同的加密 |
| `getNString` | 与 `getString` 相同解密 |
| 重复列名 | 后写覆盖；并增加 `表.列` key + MetaData 回退 |
| Statement skip-comment | `executeQuery` 命中 skip 注释则不解密包装 |
| SELECT 函数投影结果解密 | `IFNULL`/`TRIM`/`CAST`/Binary/`CASE`/`LAG` 等及标量子查询投影：登记源列并解密**返回值**（多加密列取第一个；别名/规范化表达式/列下标匹配） |

## 仍不支持 / 仅日志

| 项 | 说明 |
|----|------|
| `INSERT` 无列名 `VALUES` | 无法安全推断列顺序 |
| Statement 字面量写库 | 不改写 SQL 字面量（风险高），请用 `PreparedStatement` |
| `CallableStatement` 字段加密 | 缺少列级元数据；`setString` 仅 debug 说明 |
| 模糊 `LIKE` | 仍走 `LikePatternHandler`；默认精确匹配 |
| 库内明文语义函数 | `UPPER(密文)`/`SUBSTRING`/`GROUP_CONCAT` 等无法等价于对明文计算；解密失败走失败策略 |
| JSON 专用表达式节点 | 部分方言 `JsonFunction`/`JsonExpression` 仍未递归抽列 |

## 相关代码

- `PlaceholderExpressionVisitor` / `JsqlparserUtil` / `PoJoEncrtptorStatementVisitor`
- `SimpleInterceptorPreparedStatement` / `ResultSetDecryptingProxy` / `SimpleInterceptorStatement`
