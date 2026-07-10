# Codex 改动记录：深度审计补测与修复

## 元信息

- Change ID：`CHANGE-20260710-deep-audit-remediation`
- 执行时间：2026-07-10 至 2026-07-11
- Execution Report：`docs/codex/execution-reports/2026-07-10-deep-audit-remediation.md`
- 基线：`72ae3beb8ab76b707c611e8895f5d40fb1bff3e6`
- 保护分支：`codex/backup-audit-pre-fix-20260710`
- 工作分支：`codex/audit-remediation-20260710`
- 最终实现 HEAD（含 scanner self-clean follow-up）：`b506a462970ccb4bb8095c07f57548f04e5c70e6`
- Status：`completed-with-residuals`
- Verification Status：`verified-local / external-validation-pending`

## 用户范围与保护措施

用户批准补充测试、生成新审计报告，并在可回滚前提下无人值守修复。所有行为改动按风险域独立提交，没有使用 `reset --hard`、`git clean` 或强推。

任务开始前已有 7 个未跟踪 Markdown 文件。它们未被人工打开、修改或暂存；早期一次全工作树 secret scanner 会机械读取未排除文本，后续验证改为对提交归档执行，最终仍只把这 7 个文件留在工作树外。

## Claims vs implementation

| ID | 实际完成项 | 主要文件 | 提交 | 验证 |
| --- | --- | --- | --- | --- |
| C01 | “清理无效下载”改为零删除只读审计 | `InvalidDownloadScanner`、`DownloadFragment` | `32b3599b` | 有效/无效/重复执行文件不变 |
| C02 | 外部 Intent 与内部导航隔离，gate 不可由任意 Scene/extras 绕过 | Manifest、`ExternalIntentActivity`、`ExternalIntentPolicy`、`MainActivity`、`StageActivity` | `d2e55bd9` | VIEW/SEND、任意 Scene、gate tests |
| C03 | 删除 shake 清锁；fixture 凭据合成化 | `SecurityScene`、parser fixtures | `d2e55bd9`、`cf89b4a7` | policy + fixture secret test |
| C04 | ZIP traversal/zip bomb/staging/host 加固；补实际字节、图片真实性和中途提交回滚 | `GZIPUtils`、`ArchiverDownloadDialog` | `897b832c`、`c213060b` | 配额、压缩比、copy mismatch、伪图片、rename failure tests |
| C05 | 认证 WebView URL/DNS/bridge redirect/local-content fail-closed，补 MyTags | policy、登录/UConfig/Profile/MyTags | `a2fee679`、`d4c3520f` | URL、IP、DNS、settings tests；native redirect/SW 残余保留 |
| C06 | FileProvider 目录组件边界 | `FileProvider` | `9163d454` | sibling prefix、encoded traversal |
| C07 | Cookie 设置不再显示/复制；手工 Cookie 字段遮罩并禁 autofill/save | `IdentityCookiePreference`、`scene_cookie_sign_in.xml` | `43d414b8`、`62987f1a`、`f7507aa1` | preference/layout security tests |
| C08 | Wi-Fi framing 支持合包、半包、UTF-8 与同步写；敏感 payload 不进日志 | `ConnectThread` | `93cecaba` | framing tests |
| C09 | 旧明文 Wi-Fi 迁移入口 fail-closed 禁用 | Manifest、advanced settings | `cf1f8dde` | component/export gate tests |
| C10 | QuickSearch、Wi-Fi 分页、scanner 热点线性化 | `EhDB`、`WiFiServerActivity`、scanner | `355e04ab`、`b238340d`、`ebc3021f` | 结果等价与 snapshot tests |
| C11 | DownloadManager 多索引与标签计数一致性 | `DownloadManager` | `2bcabd13` | add/move/delete/batch/order invariants |
| C12 | manifest 可恢复替换、同内容合并、完成状态基于磁盘 | `LibraryManifest`、`LibraryScanner` | `9652e96f` | backup/temp/corrupt primary/stale finish |
| C13 | SAF 未知长度、零字节、不可用根目录与 pages=0 被正确区分 | scanners、`LibraryImageFilePolicy` | `af94add5` | policy + scanner tests |
| C14 | 有界 strict UTF-8 导入；新导出改版本化 JSONL 并保持持久化 nullable/unknown 字段自兼容 | parser、`DownloadFragment`、device test | `647c0c7a`、`7242b2f2`、`1152ce0c`、`44564514`、`a7d93c67` | round-trip、schema、numeric、API23/35 |
| C15 | legacy DB 单事务/pending/稳定主键/真实 commit-marker replay | `EhDB` | `6905049c`、`7242b2f2` | rollback、retry、跨重启 replay |
| C16 | 通知 PendingIntent immutable 且 identity 隔离 | factory、`DownloadService` | `62987f1a` | token/extras JVM tests；真实通知设备 smoke 待办 |
| C17 | 依赖/CI/secret 门禁：去重、checksum、strict lock、peeled Actions、跨平台 AAPT2、scanner 自身不误报 | build、lockfiles、workflows、scanner、metadata | `12769fde`、`1c655235`、`fcf797d8`、`94c93885`、`3837d23f`、`b506a462` | strict debug/release/connected/DAO；scanner self-test + archived HEAD scan |
| C18 | README、架构、开发、部署、排障与审计证据 | `README.md`、`docs/*.md` | `6c4da473`、`fc372bf7`、本文所在修订提交 | 链接、命令、variant 与 CI 对照 |

## 性能等价说明

| 路径 | 之前 | 之后 | 行为约束 |
| --- | --- | --- | --- |
| QuickSearch 接管 | `O(existing × incoming)` | `O(existing + incoming)` | 保持顺序与去重；同批重复过滤 |
| Wi-Fi server 分页 | `remove(0)` 约 `O(n²)` | index 读取 `O(n)` | 输出页序不变 |
| 标签排序 | mutation 循环内重复 sort | 每个受影响 bucket 一次 `O(k log k)` | 日期降序不变 |
| invalid scanner | list/find/二次遍历 | 单目录 snapshot + 未知长度一字节 probe | 新增 unavailable/empty/unreadable issue 语义，不删除 |
| DB migration | 每行 insert、部分成功 | 完整读取 + 单事务 batch + stable replay key | 失败目标库不变；marker 丢失可幂等重放 |

## 提交与回滚

- 基线到 `b506a462` 共 34 个小提交（含计划、维护文档、最终报告和 scanner follow-up）；本文修订由其后的文档提交保存。
- `codex/backup-audit-pre-fix-20260710` 是任务前取证/对比基线；它包含已清理的 fixture 与明文 Wi-Fi 风险，不可直接部署。
- 回滚同一风险域时应按依赖链逆序 `git revert` 并重跑门禁；跨多个提交的 JSONL、归档和供应链修复不能假定单个 revert 后仍可构建或安全。
- 不得使用破坏性 reset/clean。
- 不得回滚恢复真实 fixture 值或重新启用明文 Wi-Fi。

## 未完成与不能自动完成

- 服务端吊销/轮换历史凭据；远端 Git 历史与 fork 协调。
- Android DownloadManager 跨域 redirect、下载前大小预算、SAF 多文件强原子/no-replace 与并发提交。
- native WebView 302/Service Worker 和真实账号页面兼容。
- 外部图片 provider 的后台有界快照、慢流/无限流/超大像素防护。
- LocalLibrary sync 与 migration 主线程 I/O 架构拆分。
- native fuzz/sanitizer、first-party coverage/mutation gate。
- release 签名、上架与线上回滚演练。
