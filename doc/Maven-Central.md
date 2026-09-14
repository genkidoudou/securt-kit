# Maven Central 发布（Portal Token）

> 完整流程见 Actions：`Release to Maven Central`  
> 命名空间：`io.github.genkidoudou`

## 401 Unauthorized 排查

构建/签名成功但上传报：

```text
Unable to upload bundle ... Status: 401
```

几乎都是 **Portal User Token** 问题，按下面检查：

1. 打开 https://central.sonatype.com/account → **Generate User Token**  
   - 会得到一对 **username + password**（都是一串 token，不是登录邮箱）
2. GitHub Secrets 必须是这对值：
   - `CENTRAL_USERNAME` = token 的 username  
   - `CENTRAL_PASSWORD` = token 的 password  
3. **不要用** 旧 OSSRH / `oss.sonatype.org` 的账号密码或 token（会 401）
4. 重新生成 Token 后，旧 Token 立即失效，Secrets 要一起更新
5. 确认 https://central.sonatype.com/publishing/namespaces 里已验证 `io.github.genkidoudou`，且生成 Token 的账号对该 namespace 有权限

本地自检（把 USER/PASS 换成 Portal Token）：

```bash
TOKEN=$(printf '%s:%s' 'USER' 'PASS' | base64 -w0)   # macOS: base64
curl -sS -o /tmp/central-auth.txt -w '%{http_code}\n' \
  -H "Authorization: Bearer ${TOKEN}" \
  https://central.sonatype.com/api/v1/publisher/deployments
# 期望 200；401 说明 token 仍不对
```

## 重新发布注意

若上次已把版本改成 `1.0.0` 但上传 401：

- 仓库若仍是 `1.0-SNAPSHOT`：再跑 workflow 即可  
- 若已变成 `1.0.0` 或已打 tag：用 `bump=custom` + `version=1.0.0`（未成功上传时可重试同版本），或发 `1.0.1`
