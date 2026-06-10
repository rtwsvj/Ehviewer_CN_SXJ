# Ehview 原生 Android Fork 阶段性工程报告

日期：2026-05-30  
本地仓库：`/Users/eric/Documents/Ehview`  
当前分支：`codex/handoff-native-port`  
上游基线：`xiaojieonly/Ehviewer_CN_SXJ` 的 `BiLi_PC_Gamer` 分支  
Eric fork：`rtwsvj/Ehviewer_CN_SXJ`  
当前 PR：`https://github.com/rtwsvj/Ehviewer_CN_SXJ/pull/1`  
PR 状态：Draft，Open  
当前 HEAD：`43e95dad66b975049cbacdfb0694b75f9a45c3a4`

## 1. 执行摘要

本项目已经从最初的 Tauri/Rust 原型需求，调整为以成熟原生 Android fork 为主线推进。当前分支基于上游 `BiLi_PC_Gamer`，已经完成三类关键工作：

- 安全加固：收敛 TLS、外部文件入口、敏感 Cookie 存储、Android 权限、FileProvider、Crashlytics 与 SQL 查询等风险面。
- 性能与复杂度优化：优化标签建议、图库过滤、数据库导入、下载批量操作、下载搜索、目录索引和导出路径。
- 本地库第一阶段：实现 manifest 标准、本地库扫描、greenDAO/SQLite 同步、Downloads 页面入口、离线/导出闭环基础。

当前代码可以构建 debug APK，并已在 Android 15 模拟器完成基础人工烟测。项目仍处于工程审核与内测阶段，不建议直接作为面向普通用户的正式发布版本。

## 2. 项目路线与关键决策

### 2.1 原始 handoff 需求

早期 `/Users/eric/Downloads/探索/eh-viewer/HANDOFF.md` 记录的产品边界包括：

- Cookie 登录。
- 系统安全存储 Cookie。
- 用户选择本地库路径。
- SQLite 本地索引。
- 作品目录与 manifest 管理。
- 下载优先阅读和边读边缓存。
- 当前阅读优先于后台下载。
- 离线模式只读本地库。
- 支持 CBZ 与全库/选中库 zip 导出。

这些需求仍然作为产品目标保留。

### 2.2 技术路线调整

原 handoff 曾把技术路线定为 Tauri Android + Rust core + Web UI。实际推进中已调整为：

- fork 现有原生 Android 项目 `xiaojieonly/Ehviewer_CN_SXJ`。
- 保留上游已有 EH 网络、登录、下载、阅读、greenDAO/SQLite、SAF/UniFile、SpiderDen、下载列表和 Android UI。
- 不迁移旧 Tauri/Rust 代码。
- 不改包名、品牌、图标和数据库 schema。

这个决策的收益是可以直接复用成熟 Android 客户端能力，把工作集中在安全、性能、本地库和离线能力上。

## 3. 仓库与 PR 状态

```text
local branch: codex/handoff-native-port
origin:      https://github.com/rtwsvj/Ehviewer_CN_SXJ.git
upstream:    https://github.com/xiaojieonly/Ehviewer_CN_SXJ.git
base:        upstream/BiLi_PC_Gamer
PR:          https://github.com/rtwsvj/Ehviewer_CN_SXJ/pull/1
PR title:    [codex] Implement native local library closed loop
PR status:   Draft / Open
```

本地工作区当前只有 `artifacts/` 未跟踪目录。该目录保存模拟器截图、UI XML、抽取的测试 DB 等证据，故意未提交到 GitHub。

## 4. 提交链

| 顺序 | Commit | 标题 | 主要内容 |
| --- | --- | --- | --- |
| 1 | `56ca124` | `Implement native handoff foundations` | 建立原生 Android fork 工作基础，接入 handoff 方向 |
| 2 | `061b6d3` | `Add selected library export actions` | 增加选中作品/本地库导出入口与任务骨架 |
| 3 | `64e1a4c` | `Harden Android security surfaces` | 安全热修复：TLS、权限、Cookie、FileProvider、外部归档入口、SQL、Crashlytics |
| 4 | `a942400` | `Resolve release lint errors` | 处理 release lint 阻塞项 |
| 5 | `00253d1` | `Add Android CI security gate` | 增加 GitHub Actions 安全检查门禁 |
| 6 | `80c804a` | `Fix JVM test Conscrypt runtime` | 修复 JVM/Robolectric 测试环境中的 Conscrypt 问题 |
| 7 | `f00594f` | `Optimize Android complexity hot paths` | 复杂度优化：标签、过滤、导入、下载批处理、搜索、目录索引 |
| 8 | `74c32b3` | `Implement local library closed loop` | 本地库 manifest、scanner、sync、导出刷新、设置入口 |
| 9 | `43e95da` | `Document local library manual testing` | 工程文档、模拟器手测记录、scanner tag 去重修复 |

相对上游基线的规模：`64 files changed, 3528 insertions(+), 475 deletions(-)`。

## 5. 主要变更范围

### 5.1 安全加固

关键文件：

- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/network_security_config.xml`
- `app/src/main/res/xml/filepaths.xml`
- `app/src/main/java/com/hippo/network/EhX509TrustManager.java`
- `app/src/main/java/com/hippo/ehviewer/client/EhCookieStore.java`
- `app/src/main/java/com/hippo/ehviewer/client/SecureCookieStorage.java`
- `app/src/main/java/com/hippo/ehviewer/gallery/ArchiveSecurity.java`
- `app/src/main/java/com/hippo/ehviewer/EhDB.java`
- `app/src/main/java/com/hippo/ehviewer/client/EhUrlOpener.kt`

已完成：

- Release 网络安全配置仅信任系统 CA；用户 CA 限制在 debug override。
- 删除 trust-all fallback，不在 TLS 初始化失败时降级为不校验证书。
- 移除或收敛高风险权限，包括 All Files Access 跳转、`REQUEST_INSTALL_PACKAGES` 等。
- 限制 Gallery/归档外部隐式入口，移除 broad `BROWSABLE` / 通配归档 VIEW surface。
- FileProvider 删除 `<root-path>`，只保留实际需要的 cache/external-cache 范围。
- 手动与恢复的身份 Cookie 强制 `Secure + HttpOnly + Path=/`。
- 移除 `CookieManager.setAcceptFileSchemeCookies(true)`。
- Cookie 凭据改为 Android Keystore 支持的安全存储辅助层。
- 黑名单查询改为参数化查询，降低 SQL 拼接风险。
- 解析异常改走 `Analytics.recordException()`，不绕过用户分析开关。
- 归档导入/读取增加大小、条目数和可读性检查。

相关测试：

- `EhDBSecurityTest`
- `EhCookieStoreTest`
- `ArchiveSecurityTest`

### 5.2 复杂度与性能优化

关键文件：

- `app/src/main/java/com/hippo/ehviewer/client/EhTagDatabase.java`
- `app/src/main/java/com/hippo/ehviewer/widget/SearchBar.java`
- `app/src/main/java/com/hippo/ehviewer/client/EhFilter.java`
- `app/src/main/java/com/hippo/ehviewer/EhDB.java`
- `app/src/main/java/com/hippo/ehviewer/download/DownloadManager.java`
- `app/src/main/java/com/hippo/ehviewer/sync/DownloadListInfosExecutor.java`
- `app/src/main/java/com/hippo/ehviewer/spider/SpiderDen.java`
- `app/src/main/java/com/hippo/ehviewer/library/LibraryExporter.java`

已完成：

- `EhTagDatabase.suggest()` 建立索引与 lowercase 缓存，减少输入时全量线性扫描。
- `SearchBar` 增加约 150ms 输入防抖，降低每字符即时查询压力。
- `EhFilter` 为 uploader/tag/tag namespace filter 建立 `HashSet` 索引。
- `EhDB.importDB()` 使用 key set 去重，减少导入时重复查询与 O(n²) 行为。
- `DownloadManager` 批量停止/删除使用 gid set 和单遍扫描，降低列表重复 contains/remove。
- `DownloadListInfosExecutor` 规范化 search tags，并批量读取缺失 `GalleryTags`，缓解下载搜索 N+1。
- `SpiderDen` 增加下载目录索引，减少批量导出/重置时重复 SAF root scan。
- `LibraryExporter` 复用目录索引，减少大库导出时重复目录查找。

相关测试：

- `EhTagDatabaseTest`
- `EhFilterTest`
- `LibraryExporterTest`
- `LibraryScannerTest`

### 5.3 本地库闭环

关键文件：

- `app/src/main/java/com/hippo/ehviewer/library/LibraryManifest.java`
- `app/src/main/java/com/hippo/ehviewer/library/LibraryScanner.java`
- `app/src/main/java/com/hippo/ehviewer/library/LibraryExporter.java`
- `app/src/main/java/com/hippo/ehviewer/download/DownloadManager.java`
- `app/src/main/java/com/hippo/ehviewer/spider/SpiderDen.java`
- `app/src/main/java/com/hippo/ehviewer/spider/SpiderQueen.java`
- `app/src/main/java/com/hippo/ehviewer/ui/fragment/DownloadFragment.java`
- `app/src/main/res/xml/download_settings.xml`

已完成：

- 定义 `manifest.json` 标准，schema 为 `ehview.library.manifest.v1`。
- Manifest 写入内容包含 source、gallery、download、reading、files。
- Manifest 读取支持缺字段、坏 JSON、旧字段容错。
- 修复 JSON roundtrip 问题：`simpleLanguage`、扁平 `tgList`、`DownloadInfo` cast。
- `LibraryScanner.scan(root)` 扫描下载根目录一级作品目录。
- 优先读取 `manifest.json`，没有 manifest 时兼容 `.ehviewer` / `SpiderInfo`。
- 扫描结果同步到既有 greenDAO/SQLite：`DOWNLOADS`、`DOWNLOAD_DIRNAME`、`Gallery_Tags`。
- 不新增数据库 schema。
- 重复扫描幂等：新作品 imported，已有作品 updated，不重复插入。
- 保留已有用户 label/time，保护 `STATE_WAIT` / `STATE_DOWNLOAD` 活动下载状态。
- 下载设置页新增“重建本地库索引”。
- 更换下载路径后触发本地库扫描。
- 下载完成、导出前、阅读进度相关路径刷新 manifest。
- 单作品 CBZ、批量 CBZ、整库 zip 导出包含最新 manifest。
- 手测发现并修复 `tgList` 与 `simpleTags` 重复标签导致 `Gallery_Tags` 重复写入的问题。

已有详细工程文档：

- `docs/local-library-engineering-review.md`

### 5.4 CI 与构建

关键文件：

- `.github/workflows/build.yml`
- `.github/workflows/fastlane.yml`
- `app/build.gradle`
- `app/src/test/resources/robolectric.properties`

已完成：

- 上游 CI 构建路线保留 JDK 21。
- 增加 Android CI security gate。
- 修复 release lint 阻塞项。
- 修复 JVM 单测 Conscrypt runtime 配置。
- 删除不再需要的 `azure-pipelines.yml`。
- 删除仓库中的测试 key 文件。

## 6. 本地库 manifest 数据模型

当前 manifest 示例：

```json
{
  "schema": "ehview.library.manifest.v1",
  "generatedAt": 1770000000000,
  "source": {
    "type": "ehentai",
    "gid": 123456,
    "token": "manualtoken",
    "galleryUrl": "https://e-hentai.org/g/123456/manualtoken/"
  },
  "gallery": {
    "gid": 123456,
    "token": "manualtoken",
    "title": "Manual Local Library Test",
    "titleJpn": "手动本地库测试",
    "uploader": "codex",
    "pages": 1,
    "simpleLanguage": "EN",
    "simpleTags": ["female:manual"],
    "tgList": ["female:manual", "artist:codex"]
  },
  "download": {
    "state": 3,
    "time": 1770000000000,
    "label": null
  },
  "reading": {
    "startPage": 0,
    "pages": 1
  },
  "files": [
    {
      "name": "00000001.jpg",
      "size": 3,
      "lastModified": 1770000000000
    }
  ]
}
```

兼容原则：

- `manifest.json` 是长期标准。
- `.ehviewer` 只作为旧目录导入兼容来源。
- 单个坏 manifest 不应中断整个 root scan。
- 不依赖新增 DB schema，避免数据库迁移风险。

## 7. 手工测试记录

手测时间：2026-05-26  
模拟器：`Codex_Ehview_API35`  
系统：Android 15  
ABI：`arm64-v8a`  
分辨率：1080 x 2400，density 420  
包名：`com.xjs.ehviewer.debug`

构建与安装：

```bash
rtk rm -rf app/build/intermediates app/build/tmp app/build/generated app/build/kspCaches app/build/outputs/apk/appRelease/debug
rtk env JAVA_HOME=/opt/homebrew/opt/openjdk@21 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools ./gradlew --no-daemon --max-workers=2 app:assembleDebug --stacktrace
rtk /opt/homebrew/share/android-commandlinetools/platform-tools/adb install -r -d app/build/outputs/apk/appRelease/debug/app-appRelease-debug.apk
```

已覆盖：

- 首次启动内容警告。
- 遥测/分析弹窗拒绝。
- Cookie 登录页可达，包含 `ipb_member_id`、`ipb_pass_hash`、`igneous` 字段。
- 主 gallery 列表展示。
- 抽屉导航展示 Downloads / Settings。
- 下载设置页展示本地库路径、重建索引、zip/CBZ 导出入口。
- 默认 `/sdcard/EhViewer/download` 在 Android 15 下不可读，重建提示 failed 1。
- 使用 app-specific file URI 后，本地样例导入成功。
- 第二次重建表现为 imported 0 / updated 1，验证幂等。
- Downloads 页面显示导入作品 `Manual Local Library Test`，状态 `Done`。
- 检查 logcat，未观察到应用级 `FATAL EXCEPTION` / `AndroidRuntime` / `ANR`。

本地证据目录：

```text
/Users/eric/Documents/Ehview/artifacts/manual-test-2026-05-26/
```

该目录包含截图、UI XML 和抽出的 `eh.db`，未提交到 GitHub。

## 8. 自动化验证记录

已经在开发过程中执行过的关键验证：

```bash
rtk xmllint --noout app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml app/src/main/res/xml/download_settings.xml
rtk git diff --check
rtk env JAVA_HOME=/opt/homebrew/opt/openjdk@21 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools ./gradlew app:testAppReleaseDebugUnitTest --tests com.hippo.ehviewer.library.LibraryManifestTest --tests com.hippo.ehviewer.library.LibraryScannerTest --stacktrace
rtk env JAVA_HOME=/opt/homebrew/opt/openjdk@21 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools ./gradlew app:testAppReleaseDebugUnitTest app:lintAppReleaseDebug app:assembleDebug --stacktrace
rtk env JAVA_HOME=/opt/homebrew/opt/openjdk@21 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools ./gradlew --no-daemon --max-workers=2 app:testAppReleaseDebugUnitTest --tests com.hippo.ehviewer.library.LibraryScannerTest --stacktrace
rtk env JAVA_HOME=/opt/homebrew/opt/openjdk@21 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools ./gradlew --no-daemon --max-workers=2 app:assembleDebug --stacktrace
```

最近一次确认：

- `LibraryScannerTest` focused test：通过。
- `app:assembleDebug`：通过。
- 当前 PR 分支已推送到 `origin/codex/handoff-native-port`。

工程师复核时建议重新执行完整命令：

```bash
rtk env JAVA_HOME=/opt/homebrew/opt/openjdk@21 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools ./gradlew --no-daemon --max-workers=2 app:testAppReleaseDebugUnitTest app:lintAppReleaseDebug app:assembleDebug --stacktrace
```

## 9. 当前可体验程度

可以体验：

- Debug APK 构建与安装。
- 登录首页和 Cookie 登录 UI。
- 原有在线浏览主流程的基础入口。
- Downloads 页面作为本地库入口。
- 下载设置中的本地库重建入口。
- 通过 manifest 样例导入本地作品到 Downloads。
- 导出入口可见，导出逻辑已有单元测试覆盖。

尚不应宣称完整可用：

- 未用真实 Eric Cookie 验证真实账号持久登录。
- 未用真实 EH 下载作品完整跑通“下载完成 -> manifest -> 离线阅读 -> 导出”。
- 模拟器手测样例图片为 3 字节占位文件，不用于验证真实图片解码。
- 真实 SAF picker 授权目录还需要工程师补测。
- 大库 1000+ 作品扫描性能还需要专项压测。

## 10. 风险与待审重点

### P0 必须复核

- 真实 Cookie 登录和重启后恢复登录。
- Android Keystore 存储在升级/清数据/异常恢复情况下的表现。
- 真实 SAF 目录选择、权限持久化、重启后恢复。
- 下载一个真实作品后 manifest 是否完整生成。
- 断网或离线模式下，已下载作品能否正常阅读。
- 单作品 CBZ、批量 CBZ、整库 zip 是否包含图片与最新 manifest。
- 安全加固是否影响旧用户已有下载目录访问。

### P1 建议复核

- 大量下载项下的 Downloads 搜索、删除、停止、标签过滤性能。
- 导入旧 `.ehviewer` 目录时的元数据完整度。
- 坏 manifest / 缺字段 manifest / 半下载目录的 scanner 容错。
- GalleryTags 从 `tgList` 与 `simpleTags` 重建后的语义一致性。
- FileProvider 收窄后，分享/打开临时文件是否仍满足应用内需求。

### P2 长期改进

- 将 `LocalLibraryScanTask` 从 `AsyncTask` 迁移到 WorkManager。
- 为大库扫描增加增量索引，基于目录 mtime 或 manifest `generatedAt` 跳过未变化目录。
- 在 UI 上给出“本地库路径不可读”的明确提示。
- 增加导出报告：导出数量、跳过数量、缺失图片列表。
- 后续再做 targetSdk、依赖、R8、libpng/OkHttp/fastjson/jsoup 等更大范围硬化。

## 11. 工程师建议审核顺序

1. 先看 PR diff 总览，确认没有超出本阶段目标的大范围品牌、包名、schema 变更。
2. 审安全 commit：`64e1a4c`、`a942400`、`00253d1`、`80c804a`。
3. 审性能 commit：`f00594f`。
4. 审本地库 commit：`74c32b3`。
5. 审文档和手测修复 commit：`43e95da`。
6. 本地跑完整 Gradle 回归命令。
7. 安装 debug APK，用真实账号和真实 SAF 目录跑手测清单。
8. 用一个旧 `.ehviewer` 下载目录验证兼容导入。
9. 用一个真实完整下载作品验证离线阅读和导出。
10. 反馈阻塞问题后再决定是否把 PR 从 Draft 转为 Ready。

## 12. 交付物清单

代码交付：

- 安全加固代码与测试。
- 性能优化代码与测试。
- 本地库 manifest/scanner/sync/export 代码与测试。
- CI/security gate 更新。
- 工程审核文档。

文档交付：

- `docs/local-library-engineering-review.md`
- `docs/project-engineering-report-2026-05-30.md`

本地证据：

- `artifacts/manual-test-2026-05-26/`

GitHub 交付：

- Draft PR：`https://github.com/rtwsvj/Ehviewer_CN_SXJ/pull/1`
- 分支：`codex/handoff-native-port`
- 最新提交：`43e95dad66b975049cbacdfb0694b75f9a45c3a4`

## 13. 当前结论

项目已经完成从“产品需求/技术路线探索”到“可审查原生 Android fork”的第一阶段转化。安全面和高频复杂度热点已有一轮处理，本地库闭环已有可构建、可测试、可手测的实现。

下一步不建议继续盲目扩大功能面。更稳的节奏是让工程师先围绕真实账号、真实 SAF、真实下载作品和真实导出做审核复测；确认闭环没有破坏现有 Ehviewer 主流程后，再推进后台扫描、增量索引、大库压测和正式可用性打磨。
