# 项目文档执行报告：深度审计补测与修复

## 元信息

- 日期：2026-07-10
- 执行状态：2026-07-11 完成；保留外部协调、真实账号与架构级残余
- 用户批准：2026-07-10，明确回复“批准执行”
- 基线提交：`72ae3beb8ab76b707c611e8895f5d40fb1bff3e6`
- 基线保护分支：`codex/backup-audit-pre-fix-20260710`
- 工作分支：`codex/audit-remediation-20260710`
- 初始工作树：仅有 7 个审计前已存在的未跟踪 Markdown 文件；未人工打开、未修改、未暂存。早期一次全工作树 scanner 会机械读取未排除文本，后续最终扫描改用提交归档。

## 用户请求

补充前次工程审计的测试盲区，生成新的审计报告；在可回滚前提下尽可能无人值守地完成报告中的修复；最后生成一份便于其他人复核的总结报告。

## 目标与执行原则

1. 先用回归测试固定已确认的破坏性、安全与一致性缺陷。
2. 每个风险域独立提交，验证失败不进入下一检查点。
3. 使用 `git revert` 回滚，不使用 `reset --hard`、`git clean` 或强推。
4. 不恢复任何已经从 fixture 中清除的敏感值。
5. 不自行发明密码协议；不能安全完成的功能采用 fail-closed 封口并记录后续设计。
6. 不把大规模依赖升级、全项目 DI/Room/Kotlin 重写混入缺陷修复。

## 计划修改范围

### 数据安全与门禁

- 下载清理改为非破坏性校验。
- 外部 Intent 与内部 Scene 路由隔离。
- 删除摇晃清空图案锁。
- 测试 fixture 使用合成凭据，并增加 secret policy。

### 输入与通信安全

- ZIP canonical containment、解压配额和独立 staging。
- WebView 请求 host/IP/redirect 安全策略。
- Wi-Fi framing、并发写和敏感日志；旧明文协议入口默认关闭。
- FileProvider 路径组件边界。
- 身份 Cookie 默认不再明文显示或复制。

### 一致性与性能

- DownloadManager 多索引不变量。
- manifest 原子写、单写者和合并 checkpoint。
- library scan/sync 的 stale finish、主线程 I/O 与 DB N+1。
- CSV 有界流式导入。
- `remove(0)`、嵌套查重和循环内排序等二次复杂度路径。
- 旧 DB 迁移的事务与可恢复状态。

### 工程治理

- dependency verification/locking、CI 安全门和覆盖率基线。
- README、架构、部署、排障、决策与验证报告。

## 预期提交切片

1. 审计记录与补充回归测试。
2. P0 下载数据保护。
3. P0 外部 Intent 与锁屏保护。
4. P0 fixture 凭据清理。
5. 归档、FileProvider 与 Cookie 安全。
6. WebView 安全策略。
7. Wi-Fi framing、日志与旧协议封口。
8. DownloadManager 不变量与批量路径。
9. Library sync 与 manifest 写入。
10. 有界导入、启动迁移、供应链、CI 与文档。

实际提交会根据测试耦合调整，但不得把无关格式化或用户文件混入。

## 验证计划

- 定向 JVM/Robolectric 测试。
- `:app:testAppReleaseDebugUnitTest`
- `:app:lintAppReleaseDebug`
- `:app:assembleAppReleaseDebug`
- `:app:compileAppReleaseDebugAndroidTestJavaWithJavac`
- 条件允许时运行 API 23、29、35 设备安全 smoke。
- 对性能路径记录复杂度前后、查询/写入次数或可复现实验。
- 最终由独立审计代理对照执行报告、Git diff、测试记录和实际代码复核。

## 风险与回滚

- 风险域使用小提交保存；跨多个提交的修复应按依赖链逆序 `git revert`，再运行完整门禁，不能假定单个 revert 后仍可构建或安全。
- 基线分支始终指向任务前 HEAD，仅用于取证/对比；它会恢复已修风险，不可直接部署。
- Wi-Fi 旧协议封口会暂时移除该功能入口；回滚会重新暴露明文协议，只能用于诊断。
- fixture 凭据清理不得恢复旧值；若影响解析，改用另一组合成值。
- 服务端凭据轮换、远端历史重写、真实账号和线上协议语义不在本地自动化权限内，必须在最终报告中标为外部动作。

## 预期交付物

- `docs/audits/2026-07-10-blindspot-supplement.md`
- `docs/codex/change-records/2026-07-10-deep-audit-remediation.md`
- `docs/codex/verification-reports/2026-07-10-deep-audit-remediation.md`
- `docs/audits/2026-07-10-remediation-final.md`
- 对应源码、测试与小步提交。

## 实际执行摘要

- 基线到实现 HEAD `3837d23f` 共 32 个小提交；最终报告另以文档提交保存。
- JVM/Robolectric 从基线 157 增至 242 tests，最终 0 failure、0 error。
- debug/release lint 均为 920 warnings + 1 hint、0 error；基线是 922 warnings + 1 hint。
- strict dependency verification 覆盖 564 components / 997 artifacts / 997 SHA-256。
- `LockMode.STRICT` 锁定 debug、release、connected-test UTP、DAO compile/runtime；不带 `--write-locks` 的 unit/lint/APK/AndroidTest/connected/DAO 路径通过。
- debug APK 27,798,141 bytes；unsigned release APK 24,368,914 bytes。
- 临时 API 23 AVD 有 8 个执行通过，另 2 个 MediaStore case 因 SDK 条件预期跳过；API 35 执行 10/10；完成后均删除。
- 用户原有 7 个未跟踪 Markdown 文件未修改或暂存；最终提交树 scanner 通过。
- 未完成项集中在服务端凭据/历史、DownloadManager redirect 与 SAF 原子性、native WebView、外部图片 provider、native fuzz、覆盖率与主线程慢路径；详见最终审计报告。

## 独立复核如何影响实现

- 安全复核发现 service PendingIntent 缺 immutable、Activity token 碰撞、Cookie 手工登录字段可复用、归档快照/回滚/图片真实性不足；均补代码与回归。
- 数据/性能复核发现 CSV 自身无法 round-trip、DB replay 测试是 no-op、零字节/未知长度页误判；均补版本化 JSONL、真实重放与三态策略。
- 构建复核发现依赖未锁、Actions 使用 annotated-tag object、AAPT2 平台 hash 与 Parcelize 版本漂移；已补 strict locks、peeled commits、跨平台 metadata。
- 第二轮只读复核又发现 JSONL nullable/unknown 持久字段和 API 23 `Double.isFinite` 兼容问题、connected-test UTP 缺锁；修复后重新执行 API 23/35 与 strict 全量门禁。
