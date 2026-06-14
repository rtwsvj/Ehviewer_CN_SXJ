# Automation seal report - 2026-06-14

生成时间: 2026-06-14 14:36:39 CST
分支: `codex/handoff-native-port`
封板代码 HEAD: `51f329da1226d7a5213e61261339425f158edfff`
报告范围: T1 MediaStore 因果自查、T2 Node 24 actions 升级、T3 emulator API 矩阵扩展。

## 1. Git / CI 对账

### 当前提交序列

以用户给定基线 `2746ce27` 为起点，本轮新增并已 push 的代码提交:

| commit | 关注点 | 说明 |
| --- | --- | --- |
| `b35318a6` | T1 | 将 `MediaStoreVisibilityDeviceTest` 改为先查不可见、再 scan、再查可见的因果结构。 |
| `d8264f42` | T2 | 将 CI actions 升级到声明 Node 24 runtime 的版本。 |
| `3bc6a945` | T3 | 将 instrumentation 矩阵扩到 API 28/29/33/35，并让 XML verifier 按 API 级别校验测试数。 |
| `a91061ff` | T1 | 改用 app external media dir，避开 CI 上直接建 `/sdcard/...` 目录失败。 |
| `f6c24e00` | T1 | 记录并处理 API 33/35 的系统自动索引行为，避免把非因果绿误报为 scan 覆盖。 |
| `851ad160` | T1 red-team | 临时把 `MediaStoreScanner.scan()` 变为 no-op，用于 CI 证伪。 |
| `51f329da` | T1 restore | 恢复 `MediaStoreScanner.scan()`，作为最终封板代码 HEAD。 |

本报告提交是 docs-only 交付提交，不改变自动化代码或 CI workflow 逻辑；最终交付回复会补充报告提交后的 HEAD/CI 对账。

### `gh run list` 对账

交付前使用未加 `--branch` 的命令对账，避免 branch filter 怪癖:

```bash
rtk gh run list --repo rtwsvj/Ehviewer_CN_SXJ --limit 12
```

关键结果:

| run | 状态 | head | 用途 |
| --- | --- | --- | --- |
| [27490309536](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27490309536) | success | `51f329da` | 恢复 scan 后最终 Android CI，全绿。 |
| [27490022644](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27490022644) | failure | `851ad160` | no-op mutation 证伪 run，API 29 connected job 按预期变红。 |
| [27489762123](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27489762123) | success | `f6c24e00` | 平台行为处理后首次全绿。 |
| [27489453601](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27489453601) | failure | `a91061ff` | API 33/35 暴露自动索引，说明不能硬写成 scan 因果。 |
| [27489123576](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27489123576) | failure | `3bc6a945` | 初版路径在 API 29+ CI 无法创建父目录。 |
| [27472455872](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27472455872) | success | `2746ce27` | 用户给定基线 run。 |

### 最终 green run job 结果

Run [27490309536](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27490309536) 结果:

| job | 结果 | 关键 XML verifier 结果 |
| --- | --- | --- |
| [android](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27490309536/job/81253995102) | success | lint + unit tests + assembleDebug 通过。 |
| [instrumentation (28)](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27490309536/job/81254187830) | success | `total=7, executed=7, failures=0, errors=0, skipped=0`。 |
| [instrumentation (29)](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27490309536/job/81254187831) | success | `total=9, executed=9, failures=0, errors=0, skipped=0`，包含 `MediaStoreVisibilityDeviceTest`。 |
| [instrumentation (33)](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27490309536/job/81254187852) | success | `total=9, executed=9, failures=0, errors=0, skipped=0`，包含 `MediaStoreVisibilityDeviceTest`。 |
| [instrumentation (35)](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27490309536/job/81254187821) | success | `total=9, executed=9, failures=0, errors=0, skipped=0`，包含 `MediaStoreVisibilityDeviceTest`。 |

## 2. T1 - MediaStoreVisibilityDeviceTest 因果化

### 目标 / 验收 / 验证方法

目标: 让测试证明 `MediaStoreScanner.scan()` 对可见性有必要性，而不是只证明文件最终可见。
验收: 恢复版 CI 绿，测试数不减；把 `scan()` 改成 no-op 后，CI connected instrumentation 必须变红。
验证方法: 在 CI 上跑正常版、no-op mutation 版、恢复版，比较结果。

### 做了什么

- `MediaStoreVisibilityDeviceTest` 不再直接插入 MediaStore row。
- 测试写入 synthetic 1x1 PNG 到 app external media dir，然后查询 `MediaStore.Images`。
- API 29 上执行严格因果路径: scan 前不可见 -> 调用 `MediaStoreScanner.scan()` -> scan 后可见。
- API 33/35 上，CI 实测文件会在 explicit scan 前被平台自动索引；测试现在明确断言并记录这个行为，然后返回，避免假装 scan 是必要条件。
- API 28 上 `@SdkSuppress(minSdkVersion = 29)` 导致 MediaStore 测试不执行，verifier 对 API 28 期望 7 个 executed tests。

### 正常版 green 证据

恢复后 run [27490309536](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27490309536) 全绿:

- API 29: `total=9, executed=9, failures=0, errors=0, skipped=0`，包含 `com.hippo.ehviewer.util.MediaStoreVisibilityDeviceTest`。
- API 33: `total=9, executed=9, failures=0, errors=0, skipped=0`，包含 `com.hippo.ehviewer.util.MediaStoreVisibilityDeviceTest`。
- API 35: `total=9, executed=9, failures=0, errors=0, skipped=0`，包含 `com.hippo.ehviewer.util.MediaStoreVisibilityDeviceTest`。
- 对照基线 33/35 仍是 9 个 executed tests，未减少。

### no-op mutation 红队证据

临时提交 `851ad160` 将 `MediaStoreScanner.scan()` 提前 return。Run [27490022644](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27490022644) 按预期失败:

- `android`: success，说明 mutation 不靠编译失败骗红。
- `instrumentation (28)`: success。
- `instrumentation (33)`: success，因为 Android 13 已在 explicit scan 前自动索引。
- `instrumentation (35)`: success，因为 Android 15 同样自动索引。
- `instrumentation (29)`: failure，connected tests 阶段红。

关键失败日志:

```text
MediaStoreVisibilityDeviceTest > externalMediaImageWithInferredMimeRequiresScannerWhenNotAutoIndexed ... FAILED
java.lang.AssertionError: Image was not visible through MediaStore.Images after scan: ehviewer-inferred-mime-...

MediaStoreVisibilityDeviceTest > externalMediaImageRequiresScannerWhenNotAutoIndexed ... FAILED
java.lang.AssertionError: Image was not visible through MediaStore.Images after scan: ehviewer-explicit-mime-...

Tests on emulator-5554 - 10 failed: There was 2 failure(s).
```

### 结论

- 真覆盖成立的范围: API 29 上，external media fixture 对 `MediaStoreScanner.scan()` 有因果必要性；no-op mutation 能让 connected CI 红。
- 仍不能删除的范围: API 33/35 上，平台会在 explicit scan 前自动索引该路径；mutation 不会让这两个 job 红，因此不能声称 33/35 证明了 scan 因果。
- 结果: **系统相册可见性人工门压窄但未删除**。删除条件仍是目标发布路径在 CI 中能证明 scan mutation 必红，或另有可靠自动化证明真实图库可见性。

## 3. T2 - CI actions 升级到 Node 24

### 目标 / 验收 / 验证方法

目标: 在 2026-06-16 GitHub Node 24 强制切换前，把 CI actions 升到声明支持 Node 24 的版本。
验收: Android CI 仍绿；Node 20 action runtime 弃用注解消失或显著减少。
验证方法: 检查 action metadata / release 信息，并在最终 CI 日志里搜索 Node 20 runtime warning。

### 做了什么

`.github/workflows/build.yml` 升级:

| action | 新版本 | Node 24 依据 |
| --- | --- | --- |
| `actions/checkout` | `v6` | [`action.yml`](https://github.com/actions/checkout/blob/v6/action.yml) 使用 `node24`。 |
| `actions/setup-java` | `v5` | [`action.yml`](https://github.com/actions/setup-java/blob/v5/action.yml) 使用 `node24`。 |
| `actions/upload-artifact` | `v7` | [`action.yml`](https://github.com/actions/upload-artifact/blob/v7/action.yml) 使用 `node24`。 |
| `actions/cache` | `v5` | [`action.yml`](https://github.com/actions/cache/blob/v5/action.yml) 使用 `node24`。 |
| `gradle/actions/wrapper-validation` | `v6` | [`wrapper-validation/action.yml`](https://github.com/gradle/actions/blob/v6/wrapper-validation/action.yml) 使用 `node24`。 |
| `reactivecircus/android-emulator-runner` | `v2.37.0` | [`action.yml`](https://github.com/ReactiveCircus/android-emulator-runner/blob/v2.37.0/action.yml) 使用 `node24`；release 文案为 Node 24 更新。 |

### 结果

- 最终 run [27490309536](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27490309536) 全绿。
- 对最终 run 日志执行:

```bash
rtk gh run view 27490309536 --repo rtwsvj/Ehviewer_CN_SXJ --log | rg -i -n "node\\.js 20|node20|FORCE_JAVASCRIPT_ACTIONS_TO_NODE24"
```

结果无匹配。日志中仍有 `deprecated` 字样，但来源是 Gradle/Kotlin/Android project deprecation warning，不是 GitHub Actions Node 20 runtime warning。

## 4. T3 - API 矩阵

### 目标 / 验收 / 验证方法

目标: 尝试补 API 28 和 API 29；稳定则加入矩阵，不稳定则列清原因。
验收: 报告最终矩阵和每个尝试 API 级别的结果。
验证方法: CI matrix 实跑，并用 XML verifier 按 API 级别核对 executed tests / classes。

### 最终矩阵

CI instrumentation matrix:

```yaml
api-level: [28, 29, 33, 35]
```

### API 级别结果

| API | 结果 | 说明 |
| --- | --- | --- |
| 28 | green | Run [27490309536](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27490309536/job/81254187830): `total=7, executed=7, failures=0, errors=0, skipped=0`。覆盖低版本 cookie / bridge / governor / download signal 类测试；MediaStore 测试因 minSdk 29 不执行。 |
| 29 | green | Run [27490309536](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27490309536/job/81254187831): `total=9, executed=9, failures=0, errors=0, skipped=0`。这是本轮 MediaStore scanner 因果 mutation 会红的关键 API。 |
| 33 | green | Run [27490309536](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27490309536/job/81254187852): `total=9, executed=9, failures=0, errors=0, skipped=0`。保留对 Android 13 权限/行为的持续覆盖，但 MediaStore 路径会自动索引。 |
| 35 | green | Run [27490309536](https://github.com/rtwsvj/Ehviewer_CN_SXJ/actions/runs/27490309536/job/81254187821): `total=9, executed=9, failures=0, errors=0, skipped=0`。保留当前 compile/target 相关高版本覆盖，但 MediaStore 路径会自动索引。 |
| 23 | 未加入 | 本轮未把 API 23 纳入 blocking matrix；原因是用户已提示 API 23 镜像常不可得/不稳定，且发布是否必须覆盖 23 是产品决策。建议先不设为 release-blocking，等产品拍板后再做 nightly/manual 或专门稳定性试跑。 |

## 5. 更新后的人工门

### 可从人工门移出的内容

- API 29 上，`MediaStoreScanner.scan()` 对 external media fixture 的 MediaStore Images 可见性已有 CI 因果证明。
- CI 现在能防止把该测试退化成 no-op scanner 假绿，至少在 API 29 上会红。

### 仍保留的人工门

- **系统相册可见性: 压窄未删。** API 33/35 的同路径会被系统自动索引，不能证明 explicit scan 是必要条件；真实图库 App 是否展示仍需要人眼或更可靠端到端方案。
- 任何真实 EH 登录、真实账号 cookie、真实站点风控/509/429 行为: 本轮按硬护栏没有触碰，仍必须 synthetic-only 或人工门。
- 需要真实网络状态、真实账号会话、真实图库 App UI 的场景: 自动化不能代替人工验收。

## 6. 待拍板

发布必须覆盖哪些 API 级别仍是产品决策，不能由本轮自动化擅自决定。

我的建议:

- 将 API 28/29/33/35 保留为当前 blocking CI matrix，前提是 CI 时长和稳定性可接受。
- API 29 应保留为 blocking，因为它是 scoped storage / MediaStore scanner 因果证明最关键的一档。
- API 28 应保留为 blocking，因为它覆盖较低版本 cookie/storage 行为，成本目前可控。
- API 33/35 应保留为 blocking，用于覆盖 Android 13+ 权限行为和当前高版本目标环境。
- API 23 暂不设为 release-blocking；如果产品要求 minSdk 23 必须 blocking，再单独投入镜像可用性和稳定性验证。

待 Eric/产品拍板:

- API 23 是否必须进入 release-blocking CI。
- API 28 是否长期 blocking，还是只在 release/nightly 跑。
- API 29 是否作为 MediaStore/scoped-storage 必跑门保留。

## 7. 没做什么及原因

- 没有使用、读取、注入任何真实 EH 账号/cookie；全程 synthetic only。
- 没有提交截图、db、apk、emulator 产物或其他二进制；`artifacts/` 未入库。
- 没有做 SpiderQueen 重构、Java -> Kotlin、DI、minify/R8 等被否决项。
- 没有 force-push、没有改写历史、没有 unshallow、没有接触 upstream。
- 本地 Gradle 编译尝试在 `compileAppReleaseDebugKotlin` 附近长期无有效输出后中断；最终验收以 GitHub Actions 的完整 CI/connected emulator 结果为准。

## 8. 关键命令

```bash
rtk gh run list --repo rtwsvj/Ehviewer_CN_SXJ --limit 12
rtk gh run view 27490309536 --repo rtwsvj/Ehviewer_CN_SXJ --json url,headSha,conclusion,status,jobs
rtk gh run view 27490309536 --repo rtwsvj/Ehviewer_CN_SXJ --job 81254187830 --log | rg -n "Instrumentation tests:|Instrumentation classes:|MediaStoreVisibilityDeviceTest"
rtk gh run view 27490309536 --repo rtwsvj/Ehviewer_CN_SXJ --job 81254187831 --log | rg -n "Instrumentation tests:|Instrumentation classes:|MediaStoreVisibilityDeviceTest"
rtk gh run view 27490309536 --repo rtwsvj/Ehviewer_CN_SXJ --job 81254187852 --log | rg -n "Instrumentation tests:|Instrumentation classes:|MediaStoreVisibilityDeviceTest"
rtk gh run view 27490309536 --repo rtwsvj/Ehviewer_CN_SXJ --job 81254187821 --log | rg -n "Instrumentation tests:|Instrumentation classes:|MediaStoreVisibilityDeviceTest"
rtk gh run view 27490022644 --repo rtwsvj/Ehviewer_CN_SXJ --log | rg -n "MediaStoreVisibilityDeviceTest|Image was not visible|Tests on emulator|FAILURE|BUILD FAILED|Instrumentation tests:"
rtk gh run view 27490309536 --repo rtwsvj/Ehviewer_CN_SXJ --log | rg -i -n "node\\.js 20|node20|FORCE_JAVASCRIPT_ACTIONS_TO_NODE24"
```

## 9. 封板结论

自动化覆盖可以按当前状态定稿，但图库可见性人工门不能完全删除。最终可信表述是:

- CI emulator 已从 API 33/35 扩展为 API 28/29/33/35。
- Node 24 actions 升级已完成，最终 Android CI 全绿。
- MediaStore scanner 因果覆盖在 API 29 成立，并有 no-op mutation 红证据。
- API 33/35 暴露系统自动索引行为，因此图库可见性门仅压窄，未删除。
