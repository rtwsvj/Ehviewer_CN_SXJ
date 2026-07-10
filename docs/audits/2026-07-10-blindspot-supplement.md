# Ehview 审计盲区补充报告

## 状态

- 报告状态：2026-07-11 完成补测、修复与两轮独立复核。
- 审计基线：`72ae3beb8ab76b707c611e8895f5d40fb1bff3e6`。
- 方法：静态调用链复核、失败回归、故障注入、JVM/Robolectric、API 23/35 设备测试、严格依赖校验。
- 结论：本地可安全修复项已落地；无法由本地自动化关闭的外部、原子性、真实账号、native 与架构残余保留在最终报告。

## 新发现的盲区与闭环

| ID / 状态 | 文件路径 | 盲区原因 | 潜在影响 | 实际处理 | 验证方式 |
| --- | --- | --- | --- | --- | --- |
| B0-1 已关闭 | `DownloadFragment.java`、`InvalidDownloadScanner.java`、`LibraryImageFilePolicy.java` | 原测试没有“任何错误输入都不得删除”的不变量，也未覆盖不可用根目录、零字节页和 SAF `length=-1` | 误删图库、把未知长度合法页判损坏 | 清理入口改为只读；0 字节、不可读与未知长度三态处理 | `InvalidDownloadScannerTest`、`LibraryImageFilePolicyTest`；重复扫描文件 hash 不变 |
| B0-2 已关闭 | Manifest、`ExternalIntentActivity.java`、`StageActivity.java`、`DownloadNotificationIntentFactory.java` | 只测正向解锁，遗漏 VIEW/SEND、任意 Scene、通知 mutability 与 PendingIntent identity | 外部入口绕 gate，或通知跳转目标互相覆盖 | Main 非导出；外部入口最小 allowlist；Scene 类型约束；通知 intent immutable 且 requestCode 隔离 | `ExternalIntentPolicyTest`、`StageActivityIntentSecurityTest`、`SecurityScenePolicyTest`、`DownloadNotificationIntentFactoryTest` |
| B0-3 核心关闭、残余保留 | `GZIPUtils.java`、`ArchiverDownloadDialog.java` | 首轮只覆盖浏览归档，遗漏下载后直接解压、实际复制增长、伪图片和中途 rename 失败 | Zip Slip、磁盘耗尽、损坏页被标完成、半图库 | 路径/配额/压缩比、私有快照、实际字节计数、图片签名+尺寸、全列表 best-effort 回滚 | `GZIPUtilsTest`、`ArchiverDownloadPolicyTest`；跨域 redirect、下载阶段大小与 SAF 多文件强原子仍未关闭 |
| B1-1 已关闭；旧协议禁用 | `ConnectThread.java`、Wi-Fi Activities | 单帧测试遗漏 TCP 合包、半帧、UTF-8 跨 chunk 和并发写 | 错帧、敏感 payload 交错或泄露 | 状态化 framing、同步写、敏感日志清理；明文功能入口禁用 | `ConnectThreadFramingTest`、`LegacyWifiSecurityGateTest` |
| B1-2 已关闭核心路径 | `DownloadManager.java` | 测试只看结果，未验证 map/list/bucket/count 的同步不变量 | UI、DB、标签计数静默分叉 | 集中维护索引与计数，批量路径一次排序 | `DownloadManagerInvariantTest`；随机并发状态机仍为后续项 |
| B1-3 部分完成 | `LibraryManifest.java`、`LibraryScanner.java`、`DownloadManager.syncLocalLibrary` | fixture 太小，无法暴露主线程 DB N+1、慢 provider 与写放大 | 大库恢复掉帧或 ANR | 完成 manifest 恢复、checkpoint 合并、stale finish、零字节/未知长度页测试 | 100/1k/10k 规模、慢 SAF 与主线程 benchmark 尚未执行 |
| B1-4 未完成 | workflows、测试目录 | CI 原“coverage”只统计 test case，不是生产代码覆盖率 | 高风险代码未执行也可能显示绿色 | 已将门禁改名为 execution gate | 仍需 JaCoCo/Kover first-party baseline、changed-line ratchet、mutation testing |
| B1-5 自动边界完成、人工待验 | `MyTagsActivity.java`、登录/Profile/UConfig WebView | 首轮认证 WebView 清单遗漏 MyTags；native WebView redirect/Service Worker 保障也未被证明 | Cookie 上下文加载恶意内容或真实页面兼容回归 | MyTags 接入精确 HTTPS host 与本地内容隔离；bridge 每跳校验 | policy/settings 单测；仍需 302、Service Worker 与真实账号 instrumentation |
| B1-6 已关闭 | `scene_cookie_sign_in.xml` | 三个可复用身份字段曾明文显示，并允许 autofill/状态保存 | 旁观、自动填充或实例状态泄露会话材料 | 密码遮罩、禁止 autofill 和保存；member id 保留数字密码键盘 | `CookieSignInLayoutSecurityTest` |
| B1-7 已关闭新格式 | `DownloadCsvParser.java`、`DownloadFragment.java` | 有界 parser 仍继承无转义 CSV；新导出无法 round-trip 逗号/换行/多标签 | 自己导出的备份可能无法恢复 | 新导出改为强制版本头 JSONL；精确整数、最小持久字段校验；旧 CSV 仅兼容输入 | `DownloadCsvParserTest`、`DownloadCsvParserDeviceTest`；API 23/35 |
| B1-8 已关闭 | `EhDB.java` | 首版“幂等”测试在 pending 已清除后重跑，实际是 no-op；QuickSearch replay 没有稳定主键 | commit 成功但 marker 未写时重放可重复插入 | legacy ID 作为稳定主键；真实重置 pending 后跨重启重放 | `EhDBLegacyMigrationTest` |
| B1-9 已关闭本地门禁 | `build.gradle`、lockfiles、verification metadata、workflows、secret scanner | 缺依赖锁；Actions 指向 annotated tag object；AAPT2 平台构件与敏感文件规则不全 | 依赖漂移、tag 替换、Linux/Windows CI 失败、密钥文件回归 | `LockMode.STRICT`；debug/release/connected-test/DAO runtime 锁；peeled commits；三平台 AAPT2 hash；scanner 扩展 | strict 无 `--write-locks` 全量与设备复跑；scanner self-test |
| B1-10 未完成 | `MainActivity.saveImageToTempFile()` | 外部 `content://` 图片在入口线程解码，缺字节/时间/像素上限 | 恶意或慢 provider 可耗内存、阻塞入口线程 | 本轮仅记录，不做高风险异步重构 | 后续需后台有界快照、magic/dimension 校验、慢流与超大流 instrumentation |
| B2-1 未完成 | `app/src/main/cpp`、archive/image JNI 入口 | 只有上游语料，没有以 App 实际 JNI 边界为 harness 的 fuzz | 畸形图片/归档可能触发 native 崩溃或越界 | 纳入后续 sanitizer/fuzz 计划 | libFuzzer corpus、ASan/UBSan CI、崩溃最小化 |

## 最终自动化证据

- JVM/Robolectric：242 tests，0 failure，0 error。
- API 23：8 个执行通过，另 2 个 MediaStore case 因 SDK 条件预期跳过；API 35：10/10。两台临时 AVD 均已删除。
- debug/release lint：各 920 warnings + 1 hint，0 error。
- strict dependency verification + strict dependency locking：debug/release APK、AndroidTest 编译、connected test、DAO compile/runtime 全部通过。
- verification metadata：564 components / 997 artifacts / 997 SHA-256。

## 不可由本地补测替代

- 已泄露凭据的服务端吊销、轮换、远端 Git 历史与 fork 协调。
- 真实 EH/ExH 账号、验证码、动态静态资源和服务端 session 语义。
- Android DownloadManager 跨域重定向逐跳控制与下载前大小限制。
- SAF 跨文件事务、并发 no-replace 提交和真实慢 provider 行为。
- 新 Wi-Fi 加密协议的互操作与密码学评审；旧协议不得恢复。
- native fuzz、生产覆盖率和 release 签名/线上回滚演练。
