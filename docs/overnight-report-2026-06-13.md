# Ehview Overnight Report - 2026-06-13

一句话总结：今晚把“能编译”的 cookie/targetSdk 35 加固推进为 8 个可独立回退的代码提交，清掉已跟踪真实 artifacts，补上 cookie/WebView/509 的红队回归测试，并完成 API 35 设备侧 Keystore 验证。

## 真实当前状态

- 分支：`codex/handoff-native-port`
- 基线：`2329faa Harden cookies and raise targetSdk to 35`
- 交付态：本报告提交后，本分支应比基线新增 9 个提交（8 个代码/验证提交 + 本报告提交），并已普通 push 到 `origin/codex/handoff-native-port`
- 最近一次完整本地验证：`./gradlew :app:testAppReleaseDebugUnitTest :app:lintAppReleaseDebug :app:assembleAppReleaseDebug` 通过
- 设备验证：API 35 AVD `Codex_Ehview_API35` 上 `SecureCookieStorageDeviceTest` 通过

## 提交记录

| Commit | 做了什么 | 为什么 | 怎么验证 |
| --- | --- | --- | --- |
| `a44a424` | 删除已跟踪 `artifacts/` 截图/XML/db，并加入 `.gitignore` | 当前快照不应继续携带真实浏览记录和大二进制 | `git ls-files artifacts` 为空；`git check-ignore -v artifacts/synthetic-placeholder.txt` 命中 `/artifacts/` |
| `c7dc505` | 修复旧版明文身份 cookie 迁移：安全存储失败时只保留 session fallback，迁移后清旧持久明文 | 红队发现安全存储失败/仅 `igneous` 边缘可能让旧明文继续持久化 | `EhCookieStoreTest` 通过；临时移除 fallback 收集后测试变红 |
| `7508282` | `WebViewCookieBridge` 对正常和畸形 `Set-Cookie` header 都按身份 cookie 名称过滤 | 红队发现 `Cookie.parse` 失败时可绕过 `includeIdentityCookies=false` | `WebViewCookieBridgeTest` 通过；临时禁用 raw-header name 过滤后测试变红 |
| `7248a7d` | 补 `RequestGovernor` interceptor 509、`/509.gif`、并发全局排队测试 | 证明手动重试/预加载/并发线程不能绕过同一个 governor 状态 | `RequestGovernorTest` 通过；临时把 509 改成 508 后测试变红 |
| `4bc9780` | 将失败退避 base/max 也做成 `Settings` 热读取参数，默认仍为 2000/16000 ms | 满足 T5 参数化要求，但不替你决定新默认值 | `RequestGovernorTest` 通过 |
| `1e69148` | 多语言资源改用 `%1$s/%2$s` 位置占位符 | 收敛 Android resource merge 的 string format warning | `:app:lintAppReleaseDebug` 通过，相关 warning 消失 |
| `191f00a` | 移除无效 `tools:replace="android:theme"` | manifest 处理提示该替换没有目标声明 | `:app:processAppReleaseDebugMainManifest :app:lintAppReleaseDebug` 通过，相关 warning 消失 |
| `21864fd` | 增加 AGP 建议的 dependency constraints sync warning suppress 属性 | 去掉每次 Gradle 配置阶段的重复非阻断提示 | `RequestGovernorTest` 通过，dependency constraints 提示消失 |

## 对抗审查结果

### A2 Cookie 加密/迁移

- 可证伪假设：如果 Keystore/安全存储在旧明文迁移时失败，旧 `okhttp3-cookie.db` 会继续持久保存 `ipb_member_id/ipb_pass_hash`。
- 攻击结果：命中；已在 `c7dc505` 修复。现在失败时当前进程可用 session cookie，DB 持久明文清除，重启后登录态丢失。
- 证据：`legacyIdentityCookiesFallbackToSessionWhenSecureStorageFails` 构造旧明文 DB + 永远失败的 fake secure storage；断言 session cookie 非 persistent、DB 无身份 cookie、重启后未登录。
- 追加边缘：仅 `igneous` 存在时也会迁移后清旧明文；`legacyOptionalIdentityCookieIsClearedAfterSecureMigration` 覆盖。
- 设备证据：API 35 AVD 上 AndroidKeyStore 加密往返设备测试通过。

### A3 WebView Cookie

- 可证伪假设：`includeIdentityCookies=false` 只在 `Cookie.parse` 成功时过滤，畸形 `Set-Cookie` 可被原样写回 WebView。
- 攻击结果：命中；已在 `7508282` 修复。现在解析失败也按 header 名称兜底过滤身份 cookie。
- 证据：`WebViewCookieBridgeTest` 覆盖正常 header、畸形 header、store 注入过滤；临时移除兜底过滤会变红。
- Grep 结果：写入 WebView cookie 的路径收敛到 `WebViewCookieBridge`；`WebViewSignInScene` 仍直接读取 `CookieManager.getCookie` 用于网页登录后的导入，这属于读回登录态，不是注入漏网。

### A1 请求治理

- 可证伪假设：509 只在 SpiderQueen 特定路径进入 cooldown，OkHttp interceptor 遇到 509 或 `/509.gif` 不一定进入全局熔断。
- 攻击结果：未打破；`RequestGovernorTest` 现在通过 interceptor 直接验证 509 status 和 509 image path 都进入 30 分钟 cooldown。
- 可证伪假设：并发线程各自为政，不能共享一个全局节流时间线。
- 攻击结果：未打破；并发测试 4 个线程共用同一 governor，完成时间至少按 1500 ms 间隔排开。
- 仍需人工观察：interceptor 中 sleep 会占用 OkHttp dispatcher 线程，策略可接受性取决于默认档位，需要你拍板。

## TargetSdk 35 行为变更核验

对照官方清单：

- Android 13 behavior changes: https://developer.android.com/about/versions/13/behavior-changes-13
- Android 14 behavior changes: https://developer.android.com/about/versions/14/behavior-changes-14
- Android 15 behavior changes: https://developer.android.com/about/versions/15/behavior-changes-15

| 项目 | 结论 | 证据/备注 |
| --- | --- | --- |
| 通知运行时权限 | 通过静态核验 | Manifest 声明 `POST_NOTIFICATIONS`；`MainActivity.onInit()` 在 API 33+ 请求权限并处理拒绝 Toast |
| 前台服务 dataSync + `onTimeout` | 通过静态核验 | Manifest 有 `FOREGROUND_SERVICE_DATA_SYNC` 和 `foregroundServiceType="dataSync"`；`DownloadService` API 35 覆盖 `onTimeout()`，调用 `stopAllDownload()` + `stopSelf(startId)` |
| Edge-to-edge | 静态未发现强制适配代码 | `GalleryActivity` 有 `WindowCompat` import，但未完成全应用视觉验证；需 API 35 人工回归首屏、阅读器、设置页、WebView 页是否被系统栏遮挡 |
| SAF 目录访问 | 静态通过，需人工路径验证 | 下载目录使用 `ACTION_OPEN_DOCUMENT_TREE`；保存图片使用 `ACTION_CREATE_DOCUMENT`；没有请求 Android 13+ `READ_MEDIA_*` |
| 媒体权限拆分 | 通过静态核验 | Manifest 的 `READ_EXTERNAL_STORAGE` 限 `maxSdkVersion=32`，没有声明 `READ_MEDIA_IMAGES/VIDEO/AUDIO`；图片选择走 picker/SAF |
| 精确闹钟 | 通过静态核验 | 未声明 `SCHEDULE_EXACT_ALARM/USE_EXACT_ALARM`，grep 未发现 `AlarmManager.setExact*` |
| 隐式广播限制 | 有潜在缺陷 | `GalleryActivity` 保存图片后仍发 `ACTION_MEDIA_SCANNER_SCAN_FILE` 隐式广播；建议改为 `MediaScannerConnection.scanFile` 或 MediaStore 路径，今晚未改 |
| Exported 组件 | 通过构建/manifest 处理 | 有 intent-filter 的主要 activity 已显式 `exported`；lint/manifest 处理通过 |

## 已知未修缺陷

1. Git 历史仍包含已推送的真实 artifacts。  
复现：`git show 2329faa:artifacts/manual-test-2026-05-26/eh.db` 仍能从历史取回。今晚只从当前快照删除，未改写历史。

2. `ACTION_MEDIA_SCANNER_SCAN_FILE` 仍在保存图片路径使用。  
复现：`rg -n "ACTION_MEDIA_SCANNER_SCAN_FILE" app/src/main/java/com/hippo/ehviewer/ui/GalleryActivity.java app/src/main/java/com/hippo/ehviewer/ui/fragment/AboutFragment.java`。Android 现代版本上可能无法可靠触发媒体扫描。

3. API 23/28/33 设备矩阵未跑。  
原因：本机仅发现 `Codex_Ehview_API35` AVD 和 android-35 platform/system-image。API 35 设备测试已通过，其他版本需要安装 AVD 后补跑。

4. 需要真实账号的 WebView 场景未做人工登录回归。  
受护栏限制，未使用 Eric 的 EH 账号。`sign-in/uconfig/mytags/profile` 只做静态路径与 synthetic cookie 测试。

5. Gradle 仍有 AGP deprecated flag 与 multidex 提示。  
我只清理了 string format、无效 `tools:replace`、dependency constraints sync warning；没有移除 Jetifier/R8/newDsl/nonFinalResIds/multidex 相关配置，避免改变构建行为。

## 待你拍板

1. RequestGovernor 默认档位  
推荐分阶段：先保留当前默认 `1500 ms / 30 min / 2-16 s` 进入小流量；若仍触发站点限额，再切到保守档 `2000-3000 ms / 60 min / 2-32 s`。  
权衡：当前档可用性更好，保守档更少触站点风控但下载体验明显变慢。

2. Firebase 遥测去留  
推荐：保留显式 opt-in 一版，发布说明写清默认关闭；如果你希望最大化隐私叙事，再彻底移除 Firebase。  
权衡：opt-in 能保留崩溃诊断通道；彻底移除能减少供应链和隐私审查面。

3. 是否清写 Git 历史  
推荐：授权后在冻结窗口用 `git filter-repo`/BFG 清掉 `artifacts/` 历史并 force-push，随后要求所有协作者重新 clone 或 hard reset。  
权衡：这是唯一真正移除历史真实浏览记录的办法；但会改写已推送历史，必须你明确授权。

## 没做什么

- 没 force-push、没 rebase、没 filter-repo/BFG。
- 没用真实 EH 账号登录。
- 没碰 `/Users/eric/Downloads/探索/eh-viewer`。提醒：它是另一个 Tauri 脚手架仓库，建议归档或标注已被本 Android 仓库取代，避免下次基线错位。
- 没大规模重构 SpiderQueen、没 Java/Kotlin 迁移、没引入 DI、没开启 minify/R8。

## 发布前人工回归清单

- API 35 真机/模拟器：首次启动，通知权限允许/拒绝两条路径。
- 真实账号：网页登录、Cookie 登录、退出登录、杀进程重启后登录态是否符合预期。
- WebView 场景：sign-in、uconfig、mytags、profile 进入/退出后检查 WebView cookie 不残留身份 cookie。
- 下载：触发普通下载、暂停/恢复、509/429 后冷却提示与 stop-all 行为。
- 存储：选择 SAF 下载目录、导出下载清单、保存图片到用户指定位置、媒体库可见性。
- Edge-to-edge：Main、Gallery 阅读器、Settings、WebView、Dialog 在 API 35 上不被状态栏/导航栏遮挡。
