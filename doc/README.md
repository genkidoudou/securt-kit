# Wiki 同步说明

仓库用户手册位于 **`doc/`**，通过 GitHub Actions [`.github/workflows/sync-wiki.yml`](../.github/workflows/sync-wiki.yml) 同步到 GitHub Wiki。

## 一次性准备

1. 仓库 **Settings → Features → Wikis** 勾选启用（首次可随便建一页 Home）。
2. 创建 Personal Access Token（classic 勾选 `repo`，或 fine-grained 对本仓 Contents 读写）。
3. 仓库 **Settings → Secrets and variables → Actions** 新增 Secret：`WIKI_TOKEN` = 该 PAT。
4. 向 `main`/`master` 推送 `doc/**` 变更，或手动 **Actions → Sync GitHub Wiki → Run workflow**。

## 同步范围

| 同步 | 不同步 |
|------|--------|
| `doc/Home.md` 等白名单页面 | 整个 `docs/`（设计 / OpenSpec） |
| `doc/_Sidebar.md` | 其它未列入 workflow 的文件 |

## Wiki 地址

`https://github.com/<owner>/<repo>/wiki`

本项目示例：https://github.com/genkidoudou/securt-kit/wiki
