# Cleanup Report - 2026-06-13

## Git state

- Repository: `/Users/eric/Documents/Ehview`
- Branch: `codex/handoff-native-port`
- Cleanup force-push snapshot HEAD: `f887b06461a341b412edbbee3eaf3dbfb8b6ef56`
- Origin branch after force-push: `f887b06461a341b412edbbee3eaf3dbfb8b6ef56`
- Original handoff HEAD before this work: `f9df0388d344f161aa82dda66d472638d85d7452`
- Pre-filter backup tip after D1/D2 commits: `fa736fc157c41f9108d2d4aff7013f49e0782dac`
- Commits added before the history rewrite: 2 (`Remove Firebase telemetry`, `Record RequestGovernor rollout decision`)
- SHA note: commit IDs changed after `git-filter-repo`; the rewritten D1/D2 commits are `722881d` and `f887b06`.
- GitHub Actions check: `gh run list --branch codex/handoff-native-port --limit 10` returned no workflow runs.

This report is a documentation delivery artifact written after the dangerous cleanup push. If committed, it is a normal follow-up doc commit, not part of the D1/D2/D3 cleanup body.

## D1 - Firebase removed

Removed:

- Root Gradle Firebase plugins: `com.google.gms.google-services`, `com.google.firebase.crashlytics`.
- App Gradle `enableFirebaseTelemetry` property gate, google-services/crashlytics plugin application, and Firebase Analytics/Crashlytics dependencies.
- Firebase reflection calls from `Analytics.kt`.
- Startup and settings UI calls that enabled/started remote analytics.
- `AnalyticsScene`, `scene_analytics.xml`, legacy GA resource file, and Firebase analytics explanation strings.

Current `Analytics` behavior:

- Public methods remain available for existing call sites: `start`, `onSceneView`, `recordException`, `isEnabled`.
- `isEnabled` is always `false`.
- `start`/`onSceneView` only write local Android log messages.
- `recordException` logs locally with `Log.e`; it no longer forwards to Crashlytics.

Verification:

- `:app:testAppReleaseDebugUnitTest :app:lintAppReleaseDebug :app:assembleAppReleaseDebug` passed with OpenJDK 21 and the existing Android SDK.
- `:app:dependencyInsight --configuration appReleaseDebugRuntimeClasspath --dependency firebase` found no matching dependencies.
- Source/build scan found no `com.google.firebase`, Firebase dependency, Crashlytics dependency, google-services plugin, or `enableFirebaseTelemetry` references in active app/build files.
- APK entry and string scans found no `firebase`, `crashlytics`, `google-services`, or `google_app_id` hits.

## D2 - RequestGovernor decision

- Decision recorded in `docs/decisions.md`.
- Defaults remain unchanged: `1500 ms` spacing, `30 min` cooldown, `2-16 s` failure backoff.
- Staged plan accepted: ship recommended defaults first; switch to a conservative profile only if production use still triggers site limits.
- No code change was made. `Settings` already hot-reads the governor enable flag, request spacing, cooldown, and failure backoff values at runtime.

## D3 - artifacts history cleanup

Backup:

- Local-only bundle: `/Users/eric/ehview-backup-20260613-0845.bundle`
- Size: 19M
- `git bundle verify /Users/eric/ehview-backup-20260613-0845.bundle` passed.
- The bundle contains real historical data and was not pushed or added to the repository.

Tooling:

- `git filter-repo` was missing initially.
- Installed with Homebrew: `brew install git-filter-repo` (`git-filter-repo` 2.47.0 bottle; `git filter-repo --version` printed `a40bce548d2c`).

Commands:

- Initial requested command `git filter-repo --path artifacts/ --invert-paths` refused because this was not a fresh clone.
- Re-ran with the same path scope plus the tool-required local force: `git filter-repo --force --path artifacts/ --invert-paths`.
- Re-added origin after filter-repo removed it: `git remote add origin https://github.com/rtwsvj/Ehviewer_CN_SXJ.git`.
- `git-filter-repo` skipped local Codex tree refs under `refs/codex/turn-diffs/*`; those local-only refs retained old artifact blobs, so they were deleted and pruned before validation.

Validation gates:

- `git log --oneline --all -- artifacts/`: empty.
- Old database blobs:
  - `e6c9cffd1bd02e6ead71f121cf29413221af2906`: `git cat-file -e` fails.
  - `8ec5ab6b3803e5a8a18940c84960c787ed6f0302`: `git cat-file -e` fails.
- Content safety: `git diff fa736fc157c41f9108d2d4aff7013f49e0782dac HEAD -- . ':(exclude)artifacts'` was empty. The old tip commit object was temporarily restored from `/tmp/ehview-backup-tip-fa736fc.commit` for this diff only, then pruned again; old artifact blobs remained missing.
- Post-filter build: `:app:assembleAppReleaseDebug` passed.

Push:

- Remote checked before push: `origin/codex/handoff-native-port` was `f9df0388d344f161aa82dda66d472638d85d7452`.
- Pushed with explicit lease: `git push --force-with-lease=refs/heads/codex/handoff-native-port:f9df0388d344f161aa82dda66d472638d85d7452 origin codex/handoff-native-port:codex/handoff-native-port`.
- Push succeeded.
- No push was made to `upstream` (`https://github.com/xiaojieonly/Ehviewer_CN_SXJ.git`).

## GitHub residuals and Eric decisions

- GitHub may still serve old commits by SHA until its garbage collection removes unreachable objects.
- The open PR can pin old commits. Fully removing GitHub-side access may require closing/deleting the PR, or in the extreme deleting/recreating the repository.
- Those GitHub-side actions are outside this authorization and need Eric's decision.
- Any collaborators should re-clone or hard reset to the rewritten `origin/codex/handoff-native-port`. This repo appears single-user, so practical impact should be limited.

## Not done

- No SpiderQueen refactor.
- No Java-to-Kotlin migration.
- No DI work.
- No minify/R8 changes.
- No real EH account was used.
- No upstream push or upstream history rewrite.
