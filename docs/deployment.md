# Ehview 部署与发布说明

## 发布边界

仓库默认可生成 debug APK；正式签名材料不应提交到 Git。当前 `app/build.gradle` 没有仓库内 signingConfig，正式发布需要维护者在受控环境提供 keystore、alias 和口令。

## 发布前检查

1. 工作树只包含计划发布的改动，审计用户/本地文件未被纳入。
2. 版本号与版本名已在 `app/build.gradle` 更新。
3. `gradle-wrapper.properties` 的 distribution SHA、依赖校验元数据与 Actions SHA 已审阅。
4. secret scanner、unit、lint、assemble、AndroidTest 编译全部通过。
5. API 23 和 target API 的定向设备测试通过。
6. 使用真实账号验证登录、验证码、UConfig、MyTags、下载、阅读、SAF、本地库恢复与导出。
7. 服务端已吊销任何历史泄露凭据；若历史重写尚未完成，在发布记录中明确声明。
8. 生成 SBOM/依赖清单并复核 JitPack 与 native 组件版本。

## 构建候选包

先生成可审计的 debug 候选：

```bash
./gradlew --dependency-verification strict \
  :app:testAppReleaseDebugUnitTest \
  :app:lintAppReleaseDebug \
  :app:assembleAppReleaseDebug
```

正式 release 应在隔离的签名环境执行相应 release variant，签名信息通过 CI secret store 或本地安全配置注入。禁止把 keystore、`key.properties`、口令或签名后的私有分发地址写入仓库。

## 产物核验

- 记录 commit、构建时间、JDK/SDK/NDK 版本、APK SHA-256、签名证书摘要。
- 安装到 API 23 与 target API 设备，至少跑启动、外部链接、应用锁、登录、下载、阅读、FileProvider 分享和归档恢复 smoke。
- 对 release 包执行 `apksigner verify --verbose --print-certs <apk>`。
- 仅从 GitHub Actions/受控构建机上传，发布说明链接到变更与验证报告。

## 回滚

- 代码回滚：对风险域提交执行 `git revert` 并重新跑完整门禁。
- 发布回滚：保留上一版已验证 APK 和签名摘要；下架/标记有问题版本，不覆盖历史产物。
- 不得通过回滚重新启用旧明文 Wi-Fi 协议或恢复已删除的真实凭据 fixture。
- 数据迁移回滚前必须验证旧版是否能读取新数据；本轮没有新增 schema，但迁移行为变化仍需保留数据库备份。
