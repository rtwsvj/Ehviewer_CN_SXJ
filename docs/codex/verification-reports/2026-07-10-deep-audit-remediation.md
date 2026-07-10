# Codex 改动核验报告：深度审计补测与修复

## 元信息

- Verification ID：`VERIFY-20260711-deep-audit-remediation`
- 日期：2026-07-11
- Scope：基线 `72ae3beb...` 到包含本报告的工作分支 HEAD。
- Related Change Record：`docs/codex/change-records/2026-07-10-deep-audit-remediation.md`
- Verdict：`partial-pass`

`partial-pass` 表示本地代码、JVM/设备自动化、debug/release 构建与供应链门禁通过；外部凭据、远端历史、真实账号、下载/SAF 强原子、native fuzz、覆盖率和架构慢路径不能由本地自动化证明。

## 1. Claims vs Evidence

| ID | 声称完成项 | 核验证据 | 状态 | 限定 |
| --- | --- | --- | --- | --- |
| V01 | 用户文件未被纳入 | 最终 status 仅保留原有 7 个未跟踪 Markdown | 已完成 | 未修改/暂存；早期 scanner 曾机械读取未排除文本 |
| V02 | 可回滚 | 保护分支指向 `72ae3beb...`；风险域小提交 | 已完成 | 基线仅供取证；依赖链逆序 revert 后必须重跑门禁 |
| V03 | 零删除下载审计 | 入口只报告；文件不变、零字节、未知长度、不可用根目录测试 | 已完成 | 不再自动修复/重置 DB |
| V04 | 外部入口不能绕 gate | Main 非导出、ExternalIntent allowlist、Scene 类型检查 | 已完成 | 真实 UI/通知 smoke 仍建议保留 |
| V05 | 归档 fail-closed | traversal/配额、私有有界快照、签名+尺寸、rename 故障回滚测试 | 部分完成 | DownloadManager redirect/下载前预算/SAF 强原子仍是残余 |
| V06 | 认证 WebView 收紧 | URL/DNS/bridge redirect policy、本地内容隔离接入四处 WebView | 部分完成 | native WebView 302/Service Worker 与真实账号未证明 |
| V07 | 旧 Wi-Fi 风险关闭 | Activity disabled/non-exported、入口删除 | 已完成（缓解） | 协议未加密，功能保持关闭 |
| V08 | 通知安全 | immutable flags、下载中/完成 token identity 分离 | 已完成核心路径 | JVM/Robolectric；真实通知设备 smoke 待办 |
| V09 | 数据索引一致性 | DownloadManager mutation 后 map/list/bucket/count tests | 已完成核心路径 | 无随机并发 stress |
| V10 | 本地库可恢复 | temp/backup、fallback、stale finish、zero/unknown length | 已完成核心故障 | provider 跨进程原子性 best-effort |
| V11 | 导入有界且新备份可恢复 | strict UTF-8/limits；versioned JSONL；nullable/unknown round-trip；精确整数 | 已完成新格式 | legacy CSV 仍只作兼容输入 |
| V12 | 旧 DB 迁移原子可重试 | transaction/pending/stable ID/真实 marker replay | 已完成一致性 | 仍在 Application 主线程 |
| V13 | 复杂度下降 | QuickSearch、remove(0)、batch sort、scanner snapshot | 已完成 | 大规模 benchmark 未建立 |
| V14 | 供应链门禁 | peeled Action commits、strict locks、scanner、564/997 checksum | 已完成本地门禁 | bootstrap 信任/签名/Linux CI 实跑/历史 scan 仍需外部证据 |
| V15 | 文档可接手 | README + architecture/development/deployment/troubleshooting | 已完成 | 旧 handoff 快照后续归档 |

## 2. 最终命令与结果

| 命令/检查 | 结果 | 证据摘要 |
| --- | --- | --- |
| 基线 unit/lint/assemble | pass | 157 tests；922 warnings + 1 hint；0 error |
| `:app:testAppReleaseDebugUnitTest` | pass | 242 tests，0 failure/error |
| `:app:lintAppReleaseDebug` | pass | 920 warnings + 1 hint，0 error |
| `:app:lintAppReleaseRelease` | pass | 920 warnings + 1 hint，0 error |
| `:app:assembleAppReleaseDebug` | pass | 27,798,141-byte APK |
| `:app:assembleAppReleaseRelease` | pass | 24,368,914-byte unsigned APK |
| AndroidTest compile | pass | Java/Kotlin/resources/package 成功 |
| `:daogenerator:classes` + runtime dependency resolution | pass | compile/runtime 均受 strict lock 约束 |
| dependency verification | pass | 564 components / 997 artifacts / 997 SHA-256 |
| dependency locking | pass | debug、release、connected-test UTP、DAO compile/runtime；无 `--write-locks` 复验 |
| secret scanner self-test | pass | 6 类 finding；输出不含 secret value |
| archived final HEAD worktree scan | pass | 不含 7 个用户未跟踪文件，无 finding |
| API 23 instrumentation | pass | 8/8，包含 JSONL minimum-API parser |
| API 35 instrumentation | pass | 10/10 |
| `git diff --check` | pass | 无 whitespace error |

设备测试类：`SecureCookieStorageDeviceTest`、`WebViewCookieBridgeDeviceTest`、`RequestGovernorMockServerDeviceTest`、`DownloadLimitSignalDeviceTest`、`DownloadCsvParserDeviceTest`、`MediaStoreVisibilityDeviceTest`。API 23 因 `@SdkSuppress(minSdkVersion=29)` 跳过 2 个 MediaStore case，实际执行 8；API 35 执行 10。临时 AVD 均已删除。

## 3. 异常、失败与处理

- 并行 Gradle 曾在可再生 `app/build/intermediates` 产生带 ` 2.xml` 后缀的重复资源；执行 `:app:clean` 后串行严格构建通过，没有删除源码。
- 首次 strict refresh 发现配置期 parent/BOM 元数据未入 verification 清单；补录可信仓库构件后保持 strict，未降级 lenient/off。
- API 35 首轮选择器使用了旧包名 `security.SecureCookieStorageDeviceTest`，失败为明确 `ClassNotFoundException`；改为实际 `client` 包后 10/10。
- 启用 `LockMode.STRICT` 后，首次真实 connected test 暴露 `androidTestUtil`/UTP 缺锁；用该设备任务生成锁后，API 23/35 均以无 `--write-locks` 命令通过。
- 归档图片测试最初证明 Robolectric 对任意字节 decode shadow 不足以代表真实格式；生产代码补 magic signature，再以真实 PNG 与伪 JPG 对抗测试验证。

## 4. 独立只读复核

三组首轮复核均给出 `partial`，并发现真实遗漏：

- 安全：PendingIntent mutability/identity、Cookie 输入、归档 TOCTOU/回滚、WebView/外部图片残余。
- 数据/性能：CSV 自身 round-trip、DB replay no-op、零字节页、LocalLibrary 慢路径。
- 构建/架构：依赖未锁、Actions annotated tag、AAPT2 平台 hash、Parcelize 版本漂移、核心/UI 反向依赖。

修复后的二轮复核确认 5 个合入阻塞已关闭，又发现并修复 JSONL `pages=0/title/thumb=null` 自兼容、API 23 `Double.isFinite` 和 numeric rounding 边界。最终复核未发现新增 P0/P1 阻塞；SAF 强原子、DownloadManager redirect 与极端像素预算明确保留为残余。

## 5. 文件与声明核验

| 类别 | 实际情况 | 结论 |
| --- | --- | --- |
| Manifest/导航 | 导出面收敛到 ExternalIntent；旧 Wi-Fi disabled | 与记录一致 |
| 安全 policy | policy 有真实调用点与负向测试 | 不是未接入占位符 |
| 数据修复 | manager/DB/manifest/parser 均有失败或不变量测试 | 与记录一致 |
| CI | workflow 调 scanner；Actions 为 peeled commit SHA；API23 lane 存在 | 与记录一致 |
| 依赖 | strict checksum + lock；debug/release/device/DAO 可解析 | 与记录一致 |
| 文档 | README 链接存在；命令与实际 variant 对照 | 与记录一致 |

## 6. 无法确认或未完成

- 服务端是否已吊销历史凭据；远端 Git/fork 是否清除旧值。
- EH/ExH 真实账号、验证码、UConfig、MyTags 和动态资源 host。
- DownloadManager 跨域 redirect、下载前磁盘预算、SAF no-replace 与跨文件原子事务。
- 外部图片 provider 的慢流/无限流/超大像素保护。
- 10,000 作品恢复时延、主线程 FrameMetrics/StrictMode。
- native 图片/归档 sanitizer/fuzz。
- production line/branch coverage；242 是 test count，不是覆盖率。
- release 签名、上架和线上回滚演练。

## 7. 核验结论

本轮本地可安全执行的修复有代码接入、独立提交、失败回归和设备证据，不存在“只写报告未实现”的整体性假完成。最终发布必须以外部动作与人工验收完成为条件，不能把本报告解释为生产安全认证。
