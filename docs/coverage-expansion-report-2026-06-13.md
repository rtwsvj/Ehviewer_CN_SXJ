# Coverage Expansion Report - 2026-06-13

## Scope And Safety

- Branch: `codex/handoff-native-port`.
- Starting baseline accepted from handoff: `0ceb06c`.
- No real EH account, real cookie, or real identity session was used. All new tests use synthetic local state, synthetic HTTP responses, or synthetic image bytes.
- No screenshots, databases, APKs, or `artifacts/` contents were added to git.
- No SDK, AVD, or system package was installed in this run, so there are no uninstall commands to record.
- Denied work was not touched: no SpiderQueen refactor, Java-to-Kotlin migration, DI migration, or minify/R8 work.

## Git And CI Reconciliation Snapshot

Pre-delivery local snapshot before this report commit:

- `git status --short --branch --untracked-files=normal`: `codex/handoff-native-port...origin/codex/handoff-native-port [ahead 2]`, clean after removing a local empty `.clean` scratch file.
- `git log --oneline --decorate -8`:
  - `c6da007f (HEAD -> codex/handoff-native-port) Add MediaStore visibility device coverage`
  - `0efb8fdb Add synthetic 509 limit signal device coverage`
  - `0ceb06c6 (origin/codex/handoff-native-port) Clarify release report delivery status`
  - `50fee911 Add release readiness report`
  - `ee78c09c Stabilize RequestGovernor concurrent schedule test`
  - `72776244 Add synthetic WebView cookie device regression`
  - `9dff3a3a Replace media scanner broadcasts with scanner API`
  - `dbe0ad72 Adapt top-level UI for Android 15 edge-to-edge`
- `gh run list --repo rtwsvj/Ehviewer_CN_SXJ --limit 10` still reflected the pre-push remote head `0ceb06c6`:
  - `27459869047` Validate Fastlane metadata, success, commit `Clarify release report delivery status`.
  - `27459869038` Build, success, commit `Clarify release report delivery status`.

This file is committed as a delivery artifact, so the final handoff message must include the post-commit/post-push reconciliation.

## Commits

### `0efb8fdb` - Add synthetic 509 limit signal device coverage

What changed:

- Added `okhttp3:mockwebserver:3.14.7` for instrumentation-only synthetic HTTP responses.
- Added `RequestGovernor.resetForTesting()` to allow deterministic cooldown assertions without real network identity.
- Added `RequestGovernorMockServerDeviceTest`, covering HTTP `509` status and `/509.gif` response paths through a local MockWebServer.
- Added `DownloadLimitNotifier` and routed `DownloadService` 509 notification creation through it, fixing the notification to use a real title plus content text.
- Added `DownloadManager.putDownloadForTesting(...)` and `isIdleForTesting()`.
- Added `DownloadLimitSignalDeviceTest`, covering synthetic `onGet509(0)` behavior: listener notification, current/waiting downloads stopped, manager idle, and visible 509 notification text.

Why:

- The old manual gate required a human to trigger and inspect a 509 limit state. The new tests are meant to prove, without a real EH account, that a synthetic 509 signal enters cooldown and that download stop-all/user-visible notification behavior is wired.

How verified:

- `./gradlew :app:compileAppReleaseDebugAndroidTestJavaWithJavac --no-daemon --max-workers=2 -Dkotlin.compiler.execution.strategy=in-process` passed with the new source.
- Direct JUnit red-team verification against existing `RequestGovernorTest`:
  - Mutated `RequestGovernor.isLimitSignal(...)` to stop treating HTTP 509 as a limit signal.
  - Recompiled the class manually.
  - `JUnitCore com.hippo.ehviewer.client.RequestGovernorTest` failed at `interceptorStartsCooldownOn509Status`.
  - Restored the production logic and reran JUnitCore; result returned to `OK (6 tests)`.

Acceptance status:

- Source-level and compile validation passed.
- The full instrumentation acceptance is not complete because the Android test APK packaging step stalled before producing an updated test APK.
- Therefore this commit reduces the 509 manual gate, but does not honestly delete it yet.

### `c6da007f` - Add MediaStore visibility device coverage

What changed:

- Added `MediaStoreVisibilityDeviceTest`, guarded to API 29+.
- Public directory path:
  - Writes a synthetic 1x1 PNG into public `Pictures/EhViewerMediaStoreScannerDeviceTest`.
  - Calls `MediaStoreScanner.scan(context, Uri.fromFile(file), "image/png")`.
  - Polls `MediaStore.Images` by display name and path.
- SAF/content path:
  - Inserts a synthetic pending `MediaStore.Images` row with `RELATIVE_PATH`.
  - Writes via `UniFile.fromMediaUri(...)`.
  - Publishes the row and calls `MediaStoreScanner.scan(context, contentUri, "image/png")`.
  - Polls `MediaStore.Images` by display name.
- Tear-down deletes rows and public files with adopted shell storage/read-media permissions.

Why:

- The previous gallery visibility gate depended on a person opening the system gallery. The new test is intended to replace that with a MediaStore query that can run on API 29/33/35 without visual judgment.

How verified:

- `./gradlew :app:compileAppReleaseDebugAndroidTestJavaWithJavac --no-daemon --max-workers=2 -Dkotlin.compiler.execution.strategy=in-process` passed with the new source.

Acceptance status:

- Source-level and compile validation passed.
- Device execution did not complete because the Android test APK packaging step stalled.
- API 29 coverage was not run because no API 29 AVD is currently installed.
- Therefore this commit reduces the gallery visibility manual gate, but does not delete it yet.

## Adversarial Review

T1 governor cooldown hypothesis:

- Falsifiable hypothesis: if production stops classifying HTTP 509 as a limit signal, the governor cooldown test must fail.
- Attack performed: temporarily removed 509 from `RequestGovernor.isLimitSignal(...)`.
- Result: existing direct JUnit coverage failed at `interceptorStartsCooldownOn509Status`; restoring the code made the suite pass again.
- Self-check: this proves the governor half of the 509 signal is not a no-op test.

T1 download stop-all/UI hypothesis:

- Falsifiable hypothesis: if `DownloadManager.onGet509(0)` does not stop current/waiting downloads, or if the 509 notification text is not posted, `DownloadLimitSignalDeviceTest` must fail.
- Attack status: not completed. The test APK installed during manual `adb install` was stale and did not contain the new class, so `am instrument` failed with class-not-found instead of exercising the assertion path.
- Self-check: because the red-team mutation was not run on-device, this part cannot be counted as accepted coverage yet.

T2 MediaStore visibility hypothesis:

- Falsifiable hypothesis: if `MediaStoreScanner.scan(...)` no longer makes public or content-backed image rows queryable through `MediaStore.Images`, `MediaStoreVisibilityDeviceTest` must fail.
- Attack status: not completed for the same Android test APK packaging blocker.
- Self-check: because the test was not executed against an updated APK, this gate remains open.

## Updated Manual Regression Gates

Deleted this round:

- None, because the instrumentation APK did not update and the new device tests did not complete. Reporting these as deleted would overstate the state of the release gate.

Reduced but still open:

- `509 end-to-end UI`: synthetic MockWebServer and download limit tests are now present and compile. The governor cooldown red-team was proven. The gate can be deleted only after the updated instrumentation APK runs and the download/UI assertions go green and red under mutation.
- `System gallery visibility`: MediaStore query tests for public and content-backed paths are now present and compile. The gate can be deleted only after API 33/35 execution passes, and API 29 coverage either runs on an installed API 29 AVD or is explicitly waived/replaced.

Still requires real account and/or human judgment:

- Any flow that validates real EH authentication, real server behavior beyond synthetic 429/509 signals, or real account-specific gallery/download authorization. This run intentionally avoided all real identity material.
- Visual polish checks that are not reducible to state/query assertions.

## Known Unfixed Issues

### Android test APK packaging stalls

Reproduction command:

```bash
rtk env JAVA_HOME=/opt/homebrew/opt/openjdk@21 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools ./gradlew :app:assembleAppReleaseDebugAndroidTest --no-daemon --max-workers=2 -Dkotlin.compiler.execution.strategy=in-process --stacktrace
```

Observed result:

- The command produced only Gradle daemon startup output, then made no visible progress for more than two minutes.
- Process inspection showed no active `javac`, `d8`, or `aapt` child process.
- A daemon thread dump showed the Gradle worker in persistent cache initialization/properties loading rather than executing Android packaging work.

Impact:

- Manual `adb install` used an older test APK.
- `am instrument` then failed class lookup for the new instrumentation classes.
- The new T1/T2 device tests cannot yet be counted as green.

### Missing API 29 AVD

- Available AVDs observed from the handoff/environment are API 23, 28, 33, and 35.
- T2 asks for API 29/33/35. No API 29 system image or AVD was installed in this run, per the no-surprise system-install guard.

## Not Done

- T3 SAF directory picker Espresso-Intents coverage was not started.
- T4 notification permission allow/deny coverage was not started.
- No push was performed before this report file was written; the final handoff must record the actual push and post-push CI state.
