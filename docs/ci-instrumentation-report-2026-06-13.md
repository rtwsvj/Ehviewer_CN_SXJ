# CI Instrumentation Report - 2026-06-13

## 1. Git/CI 对账后的真实状态

- 仓库: `/Users/eric/Documents/Ehview`
- 分支: `codex/handoff-native-port`
- 基线: `2ea58d1a Record coverage expansion status`
- 本轮代码验证 HEAD: `327d92c5426a8cc9d4f4639b119bd95dbbb7fae7`
- 推送: 已推到 `origin/codex/handoff-native-port`
- 对账命令: `gh run list --repo rtwsvj/Ehviewer_CN_SXJ --workflow Build --limit 12`
- 最新恢复绿 CI: [Build 27472123713](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27472123713), conclusion `success`, head SHA `327d92c5426a8cc9d4f4639b119bd95dbbb7fae7`

CI job 结果:

| Run | Commit | Job | Result | Timing |
| --- | --- | --- | --- | --- |
| [27472123713](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27472123713) | `327d92c5` restore notifier | `android` | success | 16:16:10-16:19:16 UTC |
| [27472123713](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27472123713) | `327d92c5` restore notifier | `instrumentation (33)` | success | 16:19:18-16:23:45 UTC |
| [27472123713](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27472123713) | `327d92c5` restore notifier | `instrumentation (35)` | success | 16:19:25-16:23:54 UTC |
| [27471812967](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27471812967) | `f5ba1846` mutate notifier | `android` | success | mutation did not fail unit/build |
| [27471812967](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27471812967) | `f5ba1846` mutate notifier | `instrumentation (33)` | failure | connected test assertion failed |
| [27471812967](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27471812967) | `f5ba1846` mutate notifier | `instrumentation (35)` | failure | connected test assertion failed |

Final green artifacts:

| Artifact | ID | Size |
| --- | --- | --- |
| `instrumentation-reports-api-33` | `7612433622` | 31,619 bytes |
| `instrumentation-reports-api-35` | `7612434904` | 32,509 bytes |
| `debug-apk` | `7612401019` | 16,443,830 bytes |
| `unit-test-reports` | `7612400803` | 81,550 bytes |
| `lint-reports` | `7612400716` | 104,857 bytes |

Local artifact hygiene:

- CI report zips were downloaded only under ignored `artifacts/ci-27471812967/`.
- `git check-ignore -v artifacts/ci-27471812967/instrumentation-reports-api-33.zip` confirmed `/artifacts/` ignore.
- `git check-ignore -v app/build/outputs/apk/example.apk` confirmed app build output ignore.
- No screenshots, DB dumps, APKs, emulator images, cookies, or real EH account material were committed.

## 2. Commits, what changed, why, and validation

| Commit | Scope | What / why / validation |
| --- | --- | --- |
| `bb771228` | CI instrumentation gate | Added a matrix `instrumentation` job using `reactivecircus/android-emulator-runner` on API 33 and 35, running `:app:connectedAppReleaseDebugAndroidTest` with the five target classes. Added XML verification requiring at least 9 tests, all expected classes, and zero failures/errors/skips. |
| `b03c994` | ignore generated outputs | Added `/app/build/` to `.gitignore` so generated Android build output and APKs cannot be accidentally staged. Verified with `git check-ignore`. |
| `c4347ef5` | emulator startup | Removed the separate AVD snapshot creation step that hung, added KVM setup, and let the emulator runner own boot/reuse. Verified by subsequent CI runs reaching connected tests. |
| `52ef07d1` | runner script invocation | Fixed the emulator runner script so Gradle is invoked on one line after exporting `TEST_CLASSES`. Verified that connected tests started instead of failing before execution. |
| `720a9fe6` | stabilize device tests | Switched MockWebServer device coverage to HTTPS using test-only OkHttp TLS helpers, avoiding Android cleartext policy. Stabilized 509 notification assertions by recreating the channel, granting notification permission, and polling active notifications. Verified by green run `27471076493` with 9 tests on API 33/35. |
| `ee1d41ff` / `8854bd12` | red-team attempt, then restore | Mutated `RequestGovernor` to stop treating HTTP 509 as a limit signal, then restored. Result: run `27471401437` failed in unit tests before instrumentation, so it is useful evidence for unit coverage but not valid connected-job mutation evidence. |
| `66562d94` / `241ccc75` | red-team attempt, then restore | Mutated `MediaStoreScanner.scan` to no-op, then restored. Result: run `27471532511` still passed on API 33/35, so this deliberately does not support deleting the gallery visibility manual gate. |
| `f5ba1846` / `327d92c5` | valid connected mutation, then restore | Mutated `DownloadLimitNotifier.show509Alert` to return before posting, then restored. Result: run `27471812967` failed only in connected instrumentation on API 33/35, and restore run `27472123713` went green. This is the valid anti-fake-green proof for the synthetic 509 notification path. |

## 3. 防假绿证据

Final green run `27472123713`, API 33:

```text
Instrumentation XML files: 1
Instrumentation tests: total=9, failures=0, errors=0, skipped=0
Instrumentation classes:
  com.hippo.ehviewer.client.RequestGovernorMockServerDeviceTest
  com.hippo.ehviewer.client.SecureCookieStorageDeviceTest
  com.hippo.ehviewer.client.WebViewCookieBridgeDeviceTest
  com.hippo.ehviewer.download.DownloadLimitSignalDeviceTest
  com.hippo.ehviewer.util.MediaStoreVisibilityDeviceTest
```

Final green run `27472123713`, API 35:

```text
Instrumentation XML files: 1
Instrumentation tests: total=9, failures=0, errors=0, skipped=0
Instrumentation classes:
  com.hippo.ehviewer.client.RequestGovernorMockServerDeviceTest
  com.hippo.ehviewer.client.SecureCookieStorageDeviceTest
  com.hippo.ehviewer.client.WebViewCookieBridgeDeviceTest
  com.hippo.ehviewer.download.DownloadLimitSignalDeviceTest
  com.hippo.ehviewer.util.MediaStoreVisibilityDeviceTest
```

Valid mutation red run `27471812967`, mutation commit `f5ba1846`:

- Mutation: `DownloadLimitNotifier.show509Alert(...)` returned before creating/posting the 509 notification.
- `android` job: success, so this was not blocked by unit/build.
- `instrumentation (33)`: failure.
- `instrumentation (35)`: failure.
- Downloaded XML report artifacts:
  - API 33 artifact `7612348703`
  - API 35 artifact `7612343076`

Parsed mutation XML:

```text
API 33: xml files=1
  tests=9, failures=1, errors=0, skipped=0
  failure=com.hippo.ehviewer.download.DownloadLimitSignalDeviceTest#notifierPostsUserVisible509CooldownCopy
  java.lang.AssertionError: 509 notification was not posted

API 35: xml files=1
  tests=9, failures=1, errors=0, skipped=0
  failure=com.hippo.ehviewer.download.DownloadLimitSignalDeviceTest#notifierPostsUserVisible509CooldownCopy
  java.lang.AssertionError: 509 notification was not posted
```

This proves the connected CI job is not a fake green for the synthetic 509 notification assertion: the same five-class filtered suite executed, went red under mutation, and returned green after restore.

Non-counted mutation attempts:

- `ee1d41ff` RequestGovernor 509 mutation failed earlier in unit tests (`RequestGovernorTest > interceptorStartsCooldownOn509Status`), so it does not prove the connected job.
- `66562d94` MediaStore no-op mutation stayed green on API 33/35. This is important negative evidence: the current MediaStore device tests prove provider visibility in those emulator environments, but they do not prove that explicit `MediaStoreScanner.scan(...)` is required on API 33/35.

## 4. T2 local instrumentation build status

Status: partially improved, not fully complete.

What improved:

- `:app:clean` cleared the previously observed generated-source pollution class of problem.
- `rg --files app/src | rg 'Binding [23]\.java$'` found no remaining `Binding 2.java` / `Binding 3.java` pollution in source sets.
- `assembleAppReleaseDebugAndroidTest` no longer stopped at Gradle cache initialization in this run. It progressed through resource generation, androidTest resource processing, Kotlin compilation, and CMake/native build phases.

Command attempted:

```bash
rtk env JAVA_HOME=/opt/homebrew/opt/openjdk@21 PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :app:clean :app:assembleAppReleaseDebugAndroidTest --no-daemon --stacktrace -Pkotlin.compiler.execution.strategy=in-process
```

Observed progress before hang:

```text
> Task :app:clean
> Task :app:mergeAppReleaseDebugResources
> Task :app:processAppReleaseDebugAndroidTestResources
> Task :app:compileAppReleaseDebugKotlin
> Task :app:buildCMakeDebug[x86_64]
> Task :app:mergeAppReleaseDebugNativeLibs
> Task :app:mergeAppReleaseDebugAndroidTestNativeLibs NO-SOURCE
> Task :app:stripAppReleaseDebugAndroidTestDebugSymbols NO-SOURCE
```

Then the local build stopped producing output. Process evidence:

- `jps -lv` showed the Gradle wrapper and one Gradle daemon still alive.
- `ps -o pid,ppid,stat,etime,%cpu,%mem,command` showed the daemon at low CPU after roughly 10 minutes.
- `jcmd <daemon> Thread.print` showed Gradle daemon/worker threads parked or waiting.
- `rg --files app/build/outputs | rg 'androidTest|appReleaseDebug.*\.apk$|\.apk$'` found no test APK before interruption.
- The build was interrupted with Ctrl-C and `./gradlew --stop` reported no remaining daemons.

Root cause assessment:

- The original cache-init deadlock was not reproduced after cleaning generated output. The local blocker moved later in the build, around native/lib merge or post-native packaging.
- The likely immediate mitigations are to keep `/app/build/` ignored, run `:app:clean` before local instrumentation APK builds when generated-source pollution is suspected, and diagnose the later Gradle/native packaging hang separately with a focused local Gradle profile or build scan if needed.
- T2 acceptance target, "local can repeatedly produce the latest test APK", is not fully met in this run. CI is the source of truth for executed device tests.

## 5. Updated manual gates

Deleted / can be removed from the manual gate only for the synthetic CI-covered slice:

- Synthetic 509 download-limit notification check: remove the need for a person to inspect that `DownloadLimitNotifier.show509Alert(...)` posts the expected 509 notification text on API 33/35. This is backed by green CI plus the notifier no-op mutation red on both APIs.

Reduced but not honestly deleted:

- 509 end-to-end real EH UI: still keep any gate that requires real EH service behavior, real account/session state, or a full user journey through production UI. This run used only synthetic state and mock/local paths by design.
- System gallery visibility: keep as "narrowed, not deleted." API 33/35 CI proves the two `MediaStoreVisibilityDeviceTest` provider-query paths execute and pass. However, the `MediaStoreScanner.scan(...)` no-op mutation did not make CI red, so the current tests do not prove explicit scanner dependency or real Gallery app visual behavior.

Still must remain manual or require a separate approved test design:

- Real EH login/session/cookie behavior involving real credentials or cookies.
- Real service-triggered 509/429 behavior beyond synthetic local/mocked signals.
- Real system gallery app visual observation, especially if API 29 or OEM gallery behavior is release-critical.

## 6. CI cost

Final green run `27472123713`:

- Overall wall clock: about 7m55s from run creation to completion.
- `android`: about 3m06s.
- `instrumentation (33)`: about 4m27s.
- `instrumentation (35)`: about 4m29s.
- Emulator matrix jobs run after `android` and in parallel with each other.

Valid mutation red run `27471812967`:

- `android`: success, about 3m18s.
- `instrumentation (33)`: failure, about 5m04s.
- `instrumentation (35)`: failure, about 4m17s.

Recommendation:

- Keep API 33/35 on every push for now because final restore run was repeatably green after the earlier stabilization commits.
- If CI minutes become painful, consider moving one API to nightly only after Eric decides which API levels are release-mandatory.

## 7. Pending decisions, not done, and reasons

Pending Eric decision:

- Whether API 23 and/or API 29 are release-mandatory CI emulator gates. I did not decide this for Eric.
- Recommended default until decided: keep push matrix at API 33 and API 35; add API 29 next if gallery scoped-storage behavior is release-critical; consider API 23 only for cookie/legacy storage coverage because `MediaStoreVisibilityDeviceTest` is API 29+ guarded.

Not done:

- No real EH account/cookie flow was run.
- No upstream/unshallow/history rewrite was performed.
- No SpiderQueen refactor, Java-to-Kotlin rewrite, DI, minify, or R8 work was touched.
- No screenshots, DB dumps, APKs, emulator images, or binary artifacts were committed.
- Local test APK production is still not proven; T2 remains partially open.

Bottom line:

- T1 is complete for API 33/35: connected instrumentation now runs in CI on push, executes 9 real tests covering the five target classes, and has a valid connected mutation red -> restore green proof for the synthetic 509 notification path.
- T3 is complete only for the synthetic 509 notification manual slice. The system gallery gate is narrowed but must not be deleted yet because the MediaStore mutation stayed green.
