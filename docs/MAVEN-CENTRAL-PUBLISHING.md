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

- 启用 `central-publishing-maven-plugin`（`publishingServerId=central`）
- 附加 sources / javadoc、GPG 签名
- 可用 `-Dcentral.autoPublish` / `-Dcentral.waitUntil` 覆盖（默认 `false` / `validated`）

上传并校验通过后，若未开 autoPublish，到 [Deployments](https://central.sonatype.com/publishing/deployments) 手动 **Publish**。

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

## GitHub Actions 自动升版发布

工作流：[`.github/workflows/release-maven-central.yml`](../.github/workflows/release-maven-central.yml)

1. 在仓库 **Settings → Secrets and variables → Actions** 配置：

| Secret | 说明 |
|--------|------|
| `CENTRAL_USERNAME` | Portal User Token 用户名 |
| `CENTRAL_PASSWORD` | Portal User Token 密码 |
| `GPG_PRIVATE_KEY` | `gpg --armor --export-secret-keys <KEYID>` 全文 |
| `GPG_PASSPHRASE` | GPG 口令（无口令可建空 secret） |
| `GPG_KEY_ID` | 可选，指纹 / key id |

### 如何拿到这三个 GPG Secret

在本机终端执行（Windows 可用 Git Bash / WSL；需已安装 [GnuPG](https://gnupg.org/)）。

**1. 若还没有密钥，先生成**

```bash
gpg --full-generate-key
```

建议：RSA 4096、有效期按需、姓名/邮箱填你的（与 Central / GitHub 一致更佳），并设置口令（即后面的 `GPG_PASSPHRASE`）。

**2. 查看 KEYID / 指纹 → `GPG_KEY_ID`**

```bash
gpg --list-secret-keys --keyid-format LONG
```

输出类似：

```text
sec   rsa4096/A1B2C3D4E5F67890 2026-01-01 [SC]
      0123456789ABCDEF0123456789ABCDEF01234567
uid           [ultimate] Your Name <you@example.com>
```

- 短/长 ID：`A1B2C3D4E5F67890`（`rsa4096/` 后面那段）
- 指纹：下一行 40 位十六进制（推荐填进 `GPG_KEY_ID`）

把指纹或长 ID 存为 GitHub Secret **`GPG_KEY_ID`**（可选；多把钥匙时建议填，避免签错钥）。

**3. 导出私钥全文 → `GPG_PRIVATE_KEY`**

```bash
# 把 <KEYID> 换成上面的指纹或长 ID
gpg --armor --export-secret-keys <KEYID>
```

复制终端里整段（含头尾）：

```text
-----BEGIN PGP PRIVATE KEY BLOCK-----
...
-----END PGP PRIVATE KEY BLOCK-----
```

整段粘贴为 Secret **`GPG_PRIVATE_KEY`**（不要少行、不要加引号）。

**4. 口令 → `GPG_PASSPHRASE`**

生成密钥时设置的口令，原样填入 **`GPG_PASSPHRASE`**。若创建时选了无口令，可建一个空值 Secret，或填空字符串。

**5. 公钥上传到密钥服务器（Central 验签需要）**

`keyserver.ubuntu.com` 经常报 `End of file`，**不要依赖它**。优先用 `keys.openpgp.org`：

```bash
# 推荐：HKPS
gpg --keyserver hkps://keys.openpgp.org --send-keys 82D47EE1B2C41DBD
```

若命令仍失败，改用网页上传（最稳）：

```bash
gpg --armor --export 82D47EE1B2C41DBD > pubkey.asc
```

1. 打开 https://keys.openpgp.org/upload  
2. 上传 `pubkey.asc`  
3. 按邮件完成验证（验证前 uid/邮箱可能不可见，验证后 Central 才能稳定查到）

可选备用服务器：

```bash
gpg --keyserver hkps://keys.mailvelope.com --send-keys 82D47EE1B2C41DBD
```

上传后自检：

```bash
gpg --keyserver hkps://keys.openpgp.org --recv-keys 82D47EE1B2C41DBD
```

**注意**：私钥只放 GitHub Actions Secrets，不要提交进仓库、不要发到聊天里。

2. **Actions → Release to Maven Central → Run workflow**
   - `bump`：`patch` / `minor` / `major` / `custom`
   - `auto_publish`：默认 `true`（Portal 校验后自动 Publish）；首次可先 `false`
   - `dry_run`：只升版 + `package`，不 deploy、不推 git

3. 成功后会：打 `vX.Y.Z` tag，并把 POM 推到下一 `X.Y.(Z+1)-SNAPSHOT`。

本地等价：

```bash
mvn -Prelease -Dcentral.autoPublish=true -Dcentral.waitUntil=published clean deploy \
  -pl securt-kit-core,securt-kit-monitor,securt-kit-mybatis,securt-kit-starter-boot2,securt-kit-starter-boot3 \
  -am
```

## 注意

- 不要把 Portal token 或 GPG 私钥提交进仓库。
- 测试工程与 playground 已跳过 deploy，勿手动发布。
- 本仓库当前默认 SCM 假定为 `https://github.com/genkidoudou/securt-kit`；若实际仓库 URL 不同，请同步改父 POM 的 `<url>` / `<scm>`。
- `waitUntil=published` 时 `autoPublish=true` 会等待 Central 发布完成，CI 可能跑较久。
