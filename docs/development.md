# Ehview 开发环境与验证

## 工具链

- JDK 21
- Gradle Wrapper 9.3.1
- Android Gradle Plugin 9.1.1
- Android SDK / compileSdk 35，minSdk 23
- Build Tools 35.0.0
- NDK 28.2.13676358
- CMake 3.22.1
- Git；Python 3 用于 CI secret scanner

macOS/Linux 示例：

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
```

Windows 使用同版本工具链并把 `./gradlew` 替换为 `gradlew.bat`。

## 第一次构建

```bash
./gradlew --dependency-verification strict \
  :app:testAppReleaseDebugUnitTest \
  :app:lintAppReleaseDebug \
  :app:assembleAppReleaseDebug \
  :app:compileAppReleaseDebugAndroidTestJavaWithJavac \
  :daogenerator:classes
```

预期输出：

- JVM/Robolectric XML：`app/build/test-results/testAppReleaseDebugUnitTest/`
- lint：`app/build/reports/lint-results-appReleaseDebug.html`
- APK：`app/build/outputs/apk/appRelease/debug/app-appRelease-debug.apk`
- AndroidTest APK：`app/build/outputs/apk/androidTest/`

## 测试分层

1. 修改时先跑受影响的单测：

   ```bash
   ./gradlew :app:testAppReleaseDebugUnitTest \
     --tests fully.qualified.TestClass
   ```

2. 提交前跑完整 unit + lint + assemble + AndroidTest 编译。
3. 外部输入、安全存储、MediaStore 等行为至少在 API 23 与当前 target API 设备上跑 instrumentation。
4. 不把测试用例数量称为“覆盖率”；覆盖率必须来自行/分支采集工具。

## 依赖校验

`gradle/verification-metadata.xml` 默认以 strict 模式阻止未知或 hash 变化的构件。普通构建不要使用 `off` 或 `lenient`。

依赖确需更新时：

```bash
./gradlew --write-verification-metadata sha256 \
  :app:testAppReleaseDebugUnitTest \
  :app:lintAppReleaseDebug \
  :app:assembleAppReleaseDebug \
  :app:compileAppReleaseDebugAndroidTestJavaWithJavac \
  :daogenerator:classes
```

随后必须：

- 审阅元数据 diff，确认只有预期 group/name/version/artifact。
- 用 `--dependency-verification strict --refresh-dependencies` 复跑。
- 不根据未知来源的 CI 失败盲目接受新 checksum。

## Git 与回滚

- 一个风险域一个提交；不要把格式化、依赖升级和行为修复混在一起。
- 回滚使用 `git revert <commit>`，不使用 `reset --hard` 或 `git clean` 处理他人工作树。
- 数据/协议修复优先提供 feature gate 或 fail-closed 状态。
- 修改 manifest、FileProvider、数据库迁移或归档逻辑时，必须新增对抗/故障注入测试。

## 并发构建限制

不要让多个 agent/终端同时对同一工作树执行 Gradle。AGP 共享 `app/build/intermediates` 时可能生成 `name 2.xml`，随后资源解析失败。处理方式：确认没有残留 Gradle 进程，执行 `./gradlew :app:clean`，再串行重跑。
