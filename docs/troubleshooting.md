# Ehview 故障排查手册

## 构建立即失败：找不到 Java

现象：`Unable to locate a Java Runtime`、class version 或 AGP toolchain 错误。

处理：确认 `JAVA_HOME` 指向 JDK 21，并用 `./gradlew --version` 核对 JVM。不要用系统旧 JRE。

## SDK/NDK/CMake 缺失

现象：platform 35、NDK 28.2.13676358 或 CMake 3.22.1 not found。

处理：用 SDK Manager 安装与 `app/build.gradle`、CI 相同的版本；确认 `ANDROID_HOME` 与 `ANDROID_SDK_ROOT` 指向同一 SDK。

## Dependency verification failed

这通常表示出现了未审阅构件或同版本构件内容变化，不应通过关闭 strict 绕过。

1. 打开 `build/reports/dependency-verification/` 报告。
2. 确认 group/name/version 是否来自预期依赖变更。
3. 从可信仓库重新获取并比对；必要时由两名维护者审阅。
4. 仅在确认可信后重新生成对应 checksum，并用 `--refresh-dependencies` 严格复跑。

## 资源文件名带 ` 2.xml`

原因：多个 Gradle 构建并发写同一 `app/build/intermediates`，不是源码资源损坏。

```bash
./gradlew :app:clean
./gradlew --dependency-verification strict :app:testAppReleaseDebugUnitTest
```

清理前先确认没有其他构建仍在运行；不要删除源码资源。

## WebView 页面空白或部分样式缺失

带 Cookie 的 WebView 现在拒绝第三方 host、非 HTTPS、非 443、IP literal、file/content/data 与 mixed content。先查看被拒 URL 是否确属页面必需资源；不要直接放宽到任意 host。若业务确需新 host，应加入最小 allowlist、验证公网 DNS/redirect，并补测试。

## 归档下载或解压被拒

检查：URL 是否为允许的 HTTPS 官方域；条目是否路径穿越、绝对路径、重复归一化路径；条目数、单文件、总展开量或压缩比是否超限。失败 staging 会被清理。不要为了兼容未知归档移除配额；应先获取最小复现并调整有证据的上限。

## 本地库恢复后状态不一致

1. 保留作品目录副本，不运行破坏性“清理”。
2. 检查 `manifest.json`、`.manifest.json.bak`、`.manifest.json.tmp` 与图片数量。
3. scanner 只根据实际页数和图片数判断完成，不盲信旧 `STATE_FINISH`。
4. 查看设置页给出的首条 warning，并保存目录结构和 manifest（先移除 Cookie/token）。

## 旧数据库迁移反复重试

迁移先完整读取五张旧表，再单事务写入；任一旧表缺失/损坏时目标库保持不变并保留 pending。备份 `data` 与 `eh.db` 后检查缺表、schema version 和日志。不要手工把 pending 置为成功；修复旧库或提供明确迁移工具后再重试。

## Wi-Fi 迁移入口不可用

这是预期的安全状态。旧协议为明文且没有对端认证，Activity 已禁用。`ConnectThread` 的 framing 修复只防止 TCP 合包/半包损坏，不提供机密性。重新启用前必须实现并评审新协议及跨版本迁移路径。

## CI 仪器测试数量不足

API < 29 预期至少 7 个已执行用例；API >= 29 预期至少 9 个，并包含 MediaStore 测试。检查 XML 是否生成、class filter 是否正确、是否出现非允许 skip。该检查不是行/分支覆盖率。
