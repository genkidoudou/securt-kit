# securt-kit-mybatis

Securt-Kit 的 **MyBatis 拦截通道**（`securtkit.encryptor.mode=MYBATIS`）。

本模块只负责「识别 table + column + value」，配置、SQL 解析、加解密策略与失败策略全部复用
`securt-kit-core`，与 JDBC 通道共用同一份加密清单，详见
[MyBatis 模式设计方案](../docs/MYBATIS-MODE-DESIGN.md)。

## 特点

- 不需要改数据源驱动与 JDBC URL（对比 `mode=JDBC` 的 `jdbc:interceptor:`）
- 不依赖 Spring，MyBatis 为 `provided` 依赖，由业务应用提供版本
- 仅在 `EncryptModeHolder.isMybatis()` 为 true 时生效，其余模式全部透传，避免双通道重复加密

## 组成

| 类 | 职责 |
|----|------|
| `EncryptInterceptor` | MyBatis 插件本体，挂在 `Executor#update` / `Executor#query` 上，串联「解析 → 加密参数 → 执行 → 解密结果 → 还原参数」 |
| `ParameterEncryptHelper` | 按占位符序号定位参数属性并加密，记录明文用于执行后还原 |
| `MpWrapperParamSupport` | MP Wrapper `paramNameValuePairs` 读写（反射，无 MP 编译依赖） |
| `ResultDecryptHelper` | 实体 / Map / List 结果解密，支持列名与下划线转驼峰属性名 |
| `DatasourceIdResolver` | 解析当前线程数据源标识，反射适配 dynamic-datasource，缺省 `default` |

## 使用

引入依赖（starter 会按 `mode` 条件装配，手写 MyBatis 配置时也可直接注册插件）：

```xml
<dependency>
    <groupId>io.github.genkidoudou</groupId>
    <artifactId>securt-kit-mybatis</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

配置（与 JDBC 模式共用同一份 `tables`）：

```yaml
securtkit:
  encryptor:
    enable: true
    mode: MYBATIS
    failure-policy: FALLBACK
    tables:
      - table-name: user
        fields:
          - field-name: phone
            strategy: com.example.AesEncryptorStrategy
```

Spring Boot 下把插件注册为 Bean 即可（`mybatis-spring-boot-starter` 会自动装配到
`SqlSessionFactory`）：

```java
@Bean
public EncryptInterceptor securtKitEncryptInterceptor() {
    return new EncryptInterceptor();
}
```

原生 MyBatis：

```xml
<plugins>
    <plugin interceptor="io.github.genkidoudou.mybatis.EncryptInterceptor"/>
</plugins>
```

## 能力范围（P0 + P1 + MP Wrapper）

| 场景 | 支持 | 说明 |
|------|------|------|
| `insert` / `update` 实体字段加密 | ✅ | `#{phone}`、`#{user.phone}` 按占位符序号定位 |
| `select` 结果映射解密（实体 / `List<实体>`） | ✅ | 按列名、别名及驼峰属性名定位 setter |
| `select` 结果解密（`Map` / `List<Map>`） | ✅ | key 忽略大小写，同时匹配列名与驼峰 key |
| WHERE `=` 参数加密 | ✅ | 单个未命名参数（参数对象即值）会整体替换后绑定 |
| WHERE `IN` / `foreach` 参数加密 | ✅ | 实体元素就地改写；不可变元素（`List<String>`）改写 BoundSql 附加参数并透传 |
| MyBatis-Plus `QueryWrapper` / `UpdateWrapper` | ✅ | `ew.paramNameValuePairs.MPGENVALn` 直写 Map；覆盖 `eq`/`in`/`between`/`set` 等 |
| `LIKE` 参数 | ⚠️ | 交给 core 的 `LikePatternHandler`，默认仅精确模式加密，其余跳过 |
| 多数据源 | ✅ | 由 `DatasourceIdResolver` 取 dynamic-datasource 上下文 |

## 实现要点

- **参数就地改写 + 还原**：加密时把业务对象上的明文替换为密文，SQL 执行结束后在 `finally`
  中还原，避免调用方拿到的实体残留密文。
- **占位符对齐**：`BoundSql#getParameterMappings()` 的顺序与 SQL 中 `?` 的顺序一致，
  因此可直接复用 core 解析出的 `insertFieldIndex`（1 起），与 JDBC 通道语义一致。
- **MP Wrapper**：不依赖 mybatis-plus 编译期包；识别 `*.paramNameValuePairs.*` 属性后反射调用
  `getParamNameValuePairs()` 读写，兼容 `ew` 及自定义 paramAlias。
- **快速路径**：先用轻量表名解析判断是否涉及加密表，不涉及则完全透传，不做完整 SQL 解析。
- **失败语义**：涉及加密表但 SQL 解析失败时抛出 `SqlParseException` 快速失败（与 JDBC 通道一致），
  避免明文静默落库；加解密自身的失败按 `failure-policy` 降级。
- **一级缓存**：结果解密是就地改写，MyBatis 一级缓存持有同一批对象引用，因此命中一级缓存时
  （`Executor#isCached`）跳过解密，避免重复解密。

## 已知限制（P2 待办）

1. **二级缓存**：`cacheEnabled=true` 且 select 开启 `useCache` 时，缓存中可能落入已解密的明文对象，
   后续命中会再次进入解密流程（`FALLBACK` 下解密失败返回原值，行为可接受但不理想）。
   MYBATIS 模式建议对加密表的查询关闭二级缓存。
2. **插件顺序**：本插件不改写 SQL 文本，但依赖 `BoundSql`。与分页插件（PageHelper、
   MyBatis-Plus `PaginationInnerInterceptor`）同用时，建议本插件注册在分页插件之后。
3. **`ExecutorType.BATCH`**：批量执行下的参数改写未完整验证。
4. **`${}` 拼接**：值不经过 `ParameterMapping`，无法加密，与 JDBC 通道一致。
5. **复杂多表别名 / 部分动态 SQL 边角**：仍依赖 core SQL 解析能否正确映射占位符到列。

## 约束

- Java 8
- 禁止在本模块引入 Spring / Spring Boot 依赖（条件装配由 starter 完成）
- 禁止新增平行配置模型，配置只允许来自 core 的 `FieldEncryptorProperties`
