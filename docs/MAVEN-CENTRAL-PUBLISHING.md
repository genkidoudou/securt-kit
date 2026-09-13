# Maven Central 发布指南

> 命名空间：`io.github.genkidoudou`  
> 通道：Central Publisher Portal + `central-publishing-maven-plugin`  
> 设计：[2026-09-13-maven-central-genkidoudou-design.md](superpowers/specs/2026-09-13-maven-central-genkidoudou-design.md)

## 发布哪些模块

| 模块 | groupId | 是否 deploy |
|------|---------|-------------|
| securt-kit（父 POM） | `io.github.genkidoudou` | 是 |
| securt-kit-core | `io.github.genkidoudou.core` | 是 |
| securt-kit-mybatis | `io.github.genkidoudou` | 是 |
| securt-kit-monitor | `io.github.genkidoudou` | 是 |
| securt-kit-starter-boot2 | `io.github.genkidoudou` | 是 |
| securt-kit-starter-boot3 | `io.github.genkidoudou` | 是 |
| playground / test-* / dy-datasource-test-* | — | **否**（`maven.deploy.skip=true`） |

## 前置条件

1. **Namespace**：在 [Central Portal](https://central.sonatype.com/) 验证 `io.github.genkidoudou`（GitHub 组织/用户关联）。
2. **User Token**：Portal → View Account → Generate User Token；写入 `~/.m2/settings.xml`：

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username><!-- token username --></username>
      <password><!-- token password --></password>
    </server>
  </servers>
</settings>
```

3. **GPG**：本机可 `gpg --list-secret-keys`，且签名可用（必要时配置 `gpg.passphrase` 或 agent）。
4. **版本**：正式发布请将各模块 `1.0-SNAPSHOT` 改为非 SNAPSHOT（例如 `1.0.0`）。Portal 对 SNAPSHOT 有单独规则，首次建议用正式版本。

## 发布命令

Boot 2 默认 reactor（含 core / mybatis / monitor / starter-boot2）：

```bash
mvn -Prelease clean deploy
```

Boot 3 starter 需在 Java 17+ 下单独带入 reactor（示例）：

```bash
mvn -Prelease clean deploy -pl securt-kit-core,securt-kit-monitor,securt-kit-mybatis,securt-kit-starter-boot3 -am
```

`release` profile 会：

- 启用 `central-publishing-maven-plugin`（`publishingServerId=central`，默认 `autoPublish=false`）
- 附加 sources / javadoc
- GPG 签名

上传并校验通过后，到 [Deployments](https://central.sonatype.com/publishing/deployments) 手动 **Publish**。若要在 CI 中自动发布，可将插件配置改为 `<autoPublish>true</autoPublish>`。

## 本地只打包、不上传

不要配置 Token / 不要激活 `release`，日常：

```bash
mvn clean package
```

## 使用者依赖坐标

```xml
<!-- Boot 2.7 -->
<dependency>
    <groupId>io.github.genkidoudou</groupId>
    <artifactId>securt-kit-starter-boot2</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Boot 3.x -->
<dependency>
    <groupId>io.github.genkidoudou</groupId>
    <artifactId>securt-kit-starter-boot3</artifactId>
    <version>1.0.0</version>
</dependency>
```

Java 包名与驱动类均在 `io.github.genkidoudou.*` 下（例如 `io.github.genkidoudou.core.interceptor.SimpleInterceptorDriver`）。

## 注意

- 不要把 Portal token 或 GPG 私钥提交进仓库。
- 测试工程与 playground 已跳过 deploy，勿手动发布。
- 本仓库当前默认 SCM 假定为 `https://github.com/genkidoudou/securt-kit`；若实际仓库 URL 不同，请同步改父 POM 的 `<url>` / `<scm>`。
