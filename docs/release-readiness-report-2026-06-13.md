# Ehview Release Readiness Report - 2026-06-13

本报告覆盖分支 `codex/handoff-native-port` 从基线 `a3a96df7 Add cleanup report`
之后的发布候选推进工作。全程未使用 Eric 的真实 EH 账号；需要登录态的验证只使用
synthetic cookie、本地设备状态或现有自动化。

## 1. Git / CI 对账

- 代码与测试检查点 HEAD: `ee78c09cb7a878b1d195e7f3a282515c8a79b104`
- 基线 HEAD: `a3a96df70ad7ff1370f58fe5e130f659a71836cd`
- 新增代码/测试提交: 4 个，均已普通 push 到 `origin/codex/handoff-native-port`
- GitHub Actions: `gh run list --branch codex/handoff-native-port --limit 10` 仅返回 `Workflow Runs`，未发现该分支的远端 CI run
- 本报告为文档提交，将在上述代码/测试检查点之后单独提交并普通 push；最终聊天交付会附提交后的 HEAD/status 对账
- 工作树在写报告前干净: `## codex/handoff-native-port...origin/codex/handoff-native-port`

## 2. 提交流水

### `dbe0ad72 Adapt top-level UI for Android 15 edge-to-edge`

- 做了什么: 移除 Android 15 edge-to-edge opt-out；新增 `EdgeToEdgeInsets`；为 Main、Toolbar 承载页、Settings、Gallery 阅读器控件接入 WindowInsets；移除迁移布局里的 `fitsSystemWindows`；修正 `GalleryHeader` 在 padding 下的测量/布局。
- 为什么: targetSdk 35 下继续依赖 opt-out 会留下 Android 15 强制 edge-to-edge 的发布风险。
- 怎么验证: `EdgeToEdgeMigrationTest` 静态守卫通过；release debug Java 编译通过；全量 unit/lint/assemble 通过。

### `9dff3a3a Replace media scanner broadcasts with scanner API`

- 做了什么: 新增 `MediaStoreScanner`，用 `MediaScannerConnection.scanFile` 替换 `GalleryActivity` 与 `AboutFragment` 中的 `ACTION_MEDIA_SCANNER_SCAN_FILE` 隐式广播；content/SAF URI 明确 no-op。
- 为什么: 隐式媒体扫描广播是已记录延期缺陷，且在现代 Android 上不可靠。
- 怎么验证: `MediaStoreScannerTest` 静态守卫通过；`rg ACTION_MEDIA_SCANNER_SCAN_FILE app/src/main/java` 无结果；全量 unit/lint/assemble 通过。

### `72776244 Add synthetic WebView cookie device regression`

- 做了什么: 新增 `WebViewCookieBridgeDeviceTest`，用 synthetic 身份 cookie 覆盖 sign-in、uconfig、mytags、profile/forum 场景；验证进入退出后 WebView `CookieManager` 不残留身份 cookie，并验证 `includeIdentityCookies=false` 时身份 cookie 不被写入、非身份 cookie 仍保留。
- 为什么: 这是发布前人工回归清单里最适合自动化替代的一项，不需要真实 EH 账号即可证明“身份 cookie 不残留”。
- 怎么验证: API 23/28/33/35 设备矩阵全部通过。

### `ee78c09c Stabilize RequestGovernor concurrent schedule test`

- 做了什么: 将 `RequestGovernorTest` 的 fake sleeper 改为同步，消除并发调度测试里的测试替身竞态。
- 为什么: 全量 unit suite 暴露出测试自身的非确定性；生产 governor 逻辑未变。
- 怎么验证: `RequestGovernorTest` 单测通过；随后全量 unit/lint/assemble 通过。

## 3. 本地验证命令与结果

- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:compileAppReleaseDebugJavaWithJavac --no-daemon --max-workers=2` - 通过。第一次 shell 环境缺 Java，设置 `JAVA_HOME` 后通过；中途因本地生成的 `Binding 2.java` / `Binding 3.java` 污染失败，执行 `:app:clean` 后消除。
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testAppReleaseDebugUnitTest --tests com.hippo.ehviewer.util.MediaStoreScannerTest --tests com.hippo.ehviewer.util.EdgeToEdgeMigrationTest --tests com.hippo.ehviewer.client.WebViewCookieBridgeTest --tests com.hippo.ehviewer.client.RequestGovernorTest --no-daemon --max-workers=2` - 通过。
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:compileAppReleaseDebugAndroidTestJavaWithJavac --no-daemon --max-workers=2` - 通过。
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testAppReleaseDebugUnitTest :app:lintAppReleaseDebug :app:assembleAppReleaseDebug --no-daemon --max-workers=2` - 通过，耗时约 5m02s。
- `rg ACTION_MEDIA_SCANNER_SCAN_FILE app/src/main/java` - 无结果。

## 4. 设备矩阵

Instrumentation 命令:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:connectedAppReleaseDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hippo.ehviewer.client.SecureCookieStorageDeviceTest,com.hippo.ehviewer.client.WebViewCookieBridgeDeviceTest \
  --no-daemon --max-workers=2
```

| API | AVD | 覆盖测试 | 结果 |
| --- | --- | --- | --- |
| 23 | `Codex_Ehview_API23` | `SecureCookieStorageDeviceTest` + `WebViewCookieBridgeDeviceTest` 2 methods | 3/3 passed |
| 28 | `Codex_Ehview_API28` | 同上 | 3/3 passed |
| 33 | `Codex_Ehview_API33` | 同上 | 3/3 passed |
| 35 | `Codex_Ehview_API35` | 同上 | 3/3 passed |

安装过的可逆 SDK/AVD 项:

- SDK: `platforms;android-23`, `platforms;android-28`, `platforms;android-33`
- System images: `system-images;android-23;google_apis;arm64-v8a`, `system-images;android-28;google_apis;arm64-v8a`, `system-images;android-33;google_apis;arm64-v8a`
- AVD: `Codex_Ehview_API23`, `Codex_Ehview_API28`, `Codex_Ehview_API33`

回滚命令:

```bash
avdmanager delete avd -n Codex_Ehview_API23
avdmanager delete avd -n Codex_Ehview_API28
avdmanager delete avd -n Codex_Ehview_API33
sdkmanager --uninstall "platforms;android-23" "platforms;android-28" "platforms;android-33" \
  "system-images;android-23;google_apis;arm64-v8a" \
  "system-images;android-28;google_apis;arm64-v8a" \
  "system-images;android-33;google_apis;arm64-v8a"
```

## 5. 对抗审查记录

| 项目 | 可证伪假设 | 实际攻击/证据 | 结论 |
| --- | --- | --- | --- |
| Edge-to-edge | 如果迁移是假的，主题 opt-out 或迁移布局 `fitsSystemWindows` 会重新出现，顶层 Activity 没有 inset 入口。 | `EdgeToEdgeMigrationTest` 检查 opt-out 与 `fitsSystemWindows`；代码入口覆盖 Main、Toolbar、Settings、Gallery；编译/lint/assemble 通过。 | 代码级守卫攻不破；视觉截图仍未完全替代。 |
| 媒体扫描 | 如果仍走旧路径，主代码会残留 `ACTION_MEDIA_SCANNER_SCAN_FILE`。 | `MediaStoreScannerTest` + `rg ACTION_MEDIA_SCANNER_SCAN_FILE app/src/main/java` 无结果。 | 已关闭旧隐式广播缺陷；系统相册可见性仍需 provider/图库级 spot-check。 |
| WebView cookie | 如果身份 cookie 会残留，synthetic `ipb_member_id`/`ipb_pass_hash`/igneous 等会在退出后仍存在。 | `WebViewCookieBridgeDeviceTest` 在 API 23/28/33/35 全部通过。 | 人工“cookie 残留”门可删除。 |
| SecureCookieStorage | 如果 Keystore/IV 修复在低版本有差异，API 23/28 上 device test 会失败。 | `SecureCookieStorageDeviceTest` 在 API 23/28/33/35 全部通过。 | A1 低版本矩阵已补证据。 |
| RequestGovernor | 如果 509/429 冷却与并发调度测试不可靠，全量 unit suite 会红。 | 修复 fake sleeper 竞态后，`RequestGovernorTest` 与全量 unit suite 通过。 | 核心 governor 行为有自动化证据；下载 UI stop-all 集成未覆盖。 |

## 6. 人工回归门更新

### 已自动化覆盖，可从人工清单删除

- WebView sign-in/uconfig/mytags/profile/forum 场景进入退出后的身份 cookie 残留检查: 已由 synthetic cookie instrumentation 覆盖，API 23/28/33/35 通过。
- SecureCookieStorage Keystore IV 低版本兼容性: 已由 API 23/28/33/35 device matrix 覆盖。
- 旧媒体扫描隐式广播是否残留: 已由静态 unit guard + grep 覆盖。
- Android 15 edge-to-edge opt-out / `fitsSystemWindows` 回归: 已由静态 unit guard 覆盖。

### 仍不可消除，必须人工或真实服务参与

- 真实 EH 账号登录成功、服务端会话语义、登出后真实服务端状态: 自动化不能使用 Eric 的真实账号，也不能证明服务端接受 synthetic cookie。
- 真实账号下 sign-in/uconfig/mytags/profile 页面业务内容是否正常显示: cookie 安全残留已自动化，但页面语义依赖真实账号与线上服务。
- 真实站点下载触发 509/429 后的用户可见冷却提示与 stop-all 体验: governor 核心已有单测，仍缺本地 mock server 驱动的端到端 UI 覆盖。
- Android 15 edge-to-edge 视觉 spot-check: Main、Gallery 阅读器、Settings、WebView 页、Dialog 在手势导航/三键导航、横屏、cutout 下是否遮挡或对比度不足；当前只有代码级与静态守卫，没有截图证据。
- 保存图片后系统相册实际可见性: 旧广播已移除，但 API 29/33/35 的图库 provider 可见性、公共目录与 SAF 路径仍需真实图库/MediaStore 观察。

## 7. 未修缺陷 / 未完成项

- T1 未完全完成视觉验收: 已做 edge-to-edge 代码适配与静态守卫，但未产出 Espresso/截图视觉证据。
- T2 未完全完成图库可见性验收: 已清除旧广播并切到 `MediaScannerConnection`，但未在 API 29/33/35 观察系统相册实际可见。
- T4 只吃掉了最高确定性的 WebView cookie 人工门；SAF 目录选择测试桩、通知权限允许/拒绝 instrumentation、509/429 本地 mock server UI 测试仍未实现。
- T5 未做依赖升级: `androidx.activity` 已是 `1.10.1`，Material 为 `1.13.0`，AppCompat 为 `1.7.0`；继续升级属于更大回归面，今晚只登记不动。
- T6 未提高 lint 严格度: 当前 `lintAppReleaseDebug` 已通过；Gradle 仍提示多项 deprecated AGP flags 与不再需要的 multidex 依赖，因可能改变构建行为，登记不动。

## 8. 待 Eric 拍板

- 是否接受“真实 EH 账号语义验证”继续保留为最小人工门。
- 是否下一轮优先投入本地 mock server 下载 UI、SAF intent stub、通知权限两路径 instrumentation，用来继续压缩人工清单。
- 是否清理或更新 Gradle deprecated flags / multidex 依赖；这会触及构建策略，未擅自处理。

## 9. 守护栏执行情况

- 未使用真实 EH 账号。
- 未 unshallow、未接触 upstream、未 force-push。
- 未做 SpiderQueen 重构、Java 到 Kotlin 迁移、DI、minify/R8。
- 每个独立关注点单独 commit；报告作为单独文档 commit。
- 系统级 SDK/AVD 安装已记录可逆项和卸载命令。
