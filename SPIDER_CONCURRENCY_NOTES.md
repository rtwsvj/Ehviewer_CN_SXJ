# SpiderQueen / SpiderDen concurrency notes

> Scope: `app/src/main/java/com/hippo/ehviewer/spider/{SpiderQueen,SpiderDen,SpiderInfo}.java`.
> Author posture: this is the highest-risk subsystem (multithreaded download engine) and cannot
> be runtime-tested in this environment (CI does not catch concurrency bugs). Default is
> **analysis + tests + tiny, concretely-justified fixes only**. Hazards below are split into
> (A) **fixed this round**, and (B) **flagged for later runtime-verified work** — deliberately
> NOT changed blind, because closing them safely needs behavioral judgment or a threading-model
> change that this round's mandate forbids.

Line numbers are against the worktree state at branch `codex/spiderqueen-concurrency`
(base `codex/handoff-native-port` @ 779d5265).

## Thread model (who touches what)

- **Queen thread** (`SpiderQueen.run` → `runInternal`): one per gallery. Fetches `SpiderInfo`,
  builds `mPageStateArray`, services the pToken request queue (`mRequestPTokenQueue`), starts the
  decode threads and the worker pool.
- **Worker threads** (`SpiderWorker.run`, up to `mWorkerMaxCount` = clamp(setting, 1, 10)):
  pull page indices off the request queues, fetch pToken/showKey, download the image bytes, write
  via `SpiderDen`, flip page state.
- **Decode threads** (`SpiderDecoder.run`, `DECODE_THREAD_NUM` = 2): pull finished indices off
  `mDecodeRequestQueue`, decode the file to an `Image`, notify listeners.
- **UI thread**: `obtainSpiderQueen` / `releaseSpiderQueen` / `setMode` / `request` / `size` /
  `getError` / add/removeListener, plus an `AsyncTask` for `putStartPage`.

## Shared mutable state inventory

| State | Type | Guard | Verdict |
|---|---|---|---|
| `mPageStateArray` (ref) | `volatile int[]` | volatile ref; elements under `mPageStateLock` | OK |
| `mPageStateArray` (elements) | `int[]` | `mPageStateLock` | OK |
| `mDownloadedPages` / `mFinishedPages` | `AtomicInteger` | atomic | OK |
| `mPageErrorMap` / `mPagePercentMap` | `ConcurrentHashMap` | concurrent | OK (see B3) |
| `mRequestPageQueue` / `2` / `mForceRequestPageQueue` | `LinkedList` | `mRequestPageQueue` monitor | OK |
| `mDownloadPage` | `volatile int` | compound r-m-w under `mRequestPageQueue` | OK |
| `mDecodeRequestQueue` | `LinkedList` | `mDecodeRequestQueue` monitor | OK |
| `mDecodeIndexArray` | `int[]` | `mDecodeRequestQueue` monitor | OK |
| `mRequestPTokenQueue` | `ConcurrentLinkedQueue` | concurrent | OK |
| `mSpiderInfo` | `AtomicReference` | atomic ref | OK ref; see B2 for contents |
| `spiderInfo.pTokenMap` | `android.util.SparseArray` | `mPTokenLock` | OK lock discipline; see B2 |
| `showKey` | `AtomicReference` | `showKeyLock` + atomic | OK; see B4 |
| `mWorkerCount` | `int` | `mWorkerLock` | OK |
| `mWorkerPoolExecutor` | ref | `mWorkerLock` | OK; see B1 |
| `mQueenThread` | `volatile Thread` | volatile | OK |
| `mError` | `volatile String` | volatile | OK |
| `receiveBytesBefore` | `long` → **`volatile long`** | none → volatile | **FIXED (A1)** |
| `mReadReference` / `mDownloadReference` | `int` | (UI-thread-confined by contract) | see B5 |
| `SpiderDen.mDownloadDir` | `UniFile` | `mDownloadDirLock` | OK |
| `SpiderDen.mMode` | `volatile int` | volatile | OK |
| `SpiderDen.mGid` | `long` | none | see B6 (benign) |

---

## (A) Fixed this round

### A1 — `receiveBytesBefore` data race  *(fixed: `volatile`)*
- **Where:** declared `SpiderQueen.java:171`; read/written `:1427` and `:1444`, inside
  `SpiderWorker.downloadImage`.
- **Hazard:** plain `long` instance field of `SpiderQueen`, read and written by every worker
  thread (up to 10) with no synchronization.
  - *Visibility:* writes by worker A have no happens-before edge to reads by worker B, so B may
    spin on a stale baseline.
  - *Atomicity/tearing:* a non-`volatile` `long` is not guaranteed to be read/written as a single
    operation (JLS allows 32-bit halves) → a worker can observe a value that was never written.
- **Interleaving:** worker A executes `receiveBytesBefore = receivedSizeA` (`:1444`) while worker
  B is mid-read at `:1427`; B sees a torn or stale long, mis-fires (or fails to fire) the 3-second
  zero-speed stall timer.
- **Fix applied:** marked the field `volatile`. This closes the visibility + tearing race
  **without** changing the cross-worker broadcast semantics or the threading model — the most
  conservative fix for "unsynchronized shared field." The deeper *logic* concern (that the field
  is shared across workers at all, so a fast worker resets a slow worker's stall baseline) is left
  to B7 because fixing it changes behavior and needs runtime verification.

---

## (B) Flagged for later runtime-verified work (NOT changed blind)

### B1 — `mWorkerPoolExecutor` nulled while `ensureWorkers` may dereference it  *(LOW, needs runtime)*
- **Where:** set to `null` in `SpiderQueen.run` at `:1026-1027` under `mWorkerLock`; `ensureWorkers`
  reads/uses it at `:600-607` under `mWorkerLock`.
- **Status:** access is consistently under `mWorkerLock`, and `ensureWorkers` already null-checks
  (`:600`). So the field itself is safe. The residual question is whether a worker already running
  inside `mWorkerPoolExecutor.execute(...)` interacts with shutdown ordering — purely a
  shutdown-time liveness concern, not a data race. Needs a real device to observe. No code change.

### B2 — `pTokenMap` lock discipline is correct, but the *map identity* is reassigned  *(LOW)*
- **Where:** `spiderInfo.pTokenMap = new SparseArray<>(...)` at `:812` (Queen, in
  `readSpiderInfoFromInternet`) and `SpiderInfo.read` builds a fresh map; workers read it via
  `mSpiderInfo.get().pTokenMap` under `mPTokenLock`.
- **Status:** the reassignment at `:812` happens on the Queen thread *before* `mSpiderInfo.lazySet`
  (`:922`) and before any worker starts (`tryToEnsureWorkers` at `:946` runs after), so there is a
  happens-before via the publication. Element-level access is consistently `mPTokenLock`-guarded.
  **No confirmed bug.** Flagged only because `SparseArray` is not thread-safe, so any *future*
  reassignment of `pTokenMap` after workers start would be a serious hazard — keep all structural
  changes to the map on the Queen thread, behind `mPTokenLock`, before publication.

### B3 — `mPagePercentMap` read in `request()` is a check-then-act over `ConcurrentHashMap`  *(LOW)*
- **Where:** `request()` reads `mPagePercentMap.get(index)` at `:573` for a `STATE_DOWNLOADING`
  page; a worker concurrently `put`s (`:1425`) or `remove`s (`:1065`).
- **Status:** map is concurrent, so no corruption; the page-state read at `:527` and the percent
  read at `:573` are two separate steps, so a transiently `null` percent can be returned for a
  page that is "downloading." UI treats `null` as "wait", which is benign. Documenting only.

### B4 — `showKey` double-checked under `showKeyLock` + `AtomicReference`  *(LOW)*
- **Where:** `downloadImage` reads `showKey.get()` at `:1220` inside `synchronized(showKeyLock)`,
  writes via `lazySet` at `:1246` (also inside the lock) and `compareAndSet(...)` at `:1285`
  (OUTSIDE the lock).
- **Status:** the `compareAndSet` at `:1285` runs without `showKeyLock` held. It is atomic so it
  cannot corrupt the reference, but it means the "clear the bad show key" step is not ordered
  against the lock-guarded read/`lazySet`. Worst case is a redundant re-fetch of the show key
  (the surrounding `for` loop already retries up to 5×). Functionally tolerant; flagged for a
  runtime check that show-key invalidation still converges under contention. No change.

### B5 — mode reference counters are not synchronized  *(LOW, contract-dependent)*
- **Where:** `mReadReference` / `mDownloadReference` (`:156-157`) mutated in
  `setMode`/`clearMode` (`:404-432`) and read in `releaseSpiderQueen` (`:248`).
- **Status:** all entry points (`obtainSpiderQueen`, `releaseSpiderQueen`) assert `OSUtils.checkMainLoop()`,
  so these are UI-thread-confined by contract → no cross-thread race today. Flagged because the
  invariant is enforced only by the `@UiThread` annotation + runtime check, not by the type system;
  a future caller off the main thread would silently corrupt the counts.

### B6 — `SpiderDen.mGid` non-volatile  *(INFO, benign)*
- **Where:** `:62`, set in ctor (`:213`) and via `setMGid` (`:249`); read by cache-key builders.
- **Status:** `setMGid` appears unused in the download path; `mGid` is effectively write-once in the
  constructor (safe publication via the `SpiderDen` final field in `SpiderQueen`). Benign today.
  If `setMGid` ever gets wired up from another thread, this becomes a real visibility race — make
  it `volatile` at that point.

### B7 — `receiveBytesBefore` is shared across workers (logic, not just memory)  *(MED, needs runtime)*
- **Where:** same field as A1.
- **Hazard (behavioral):** the zero-speed stall detector compares each worker's `receivedSize`
  against a single shared baseline. Two concurrent downloads continuously overwrite each other's
  baseline, so the 3-second "download stalled" timer can be reset by an unrelated worker making
  progress, masking a genuinely stuck download (or, conversely, mis-firing). The field almost
  certainly should be **per-`SpiderWorker`** (it already lives next to per-worker state like
  `downloadSpeedZeroTimeCount` / `cancelDownload`).
- **Why not fixed now:** moving it into `SpiderWorker` is a *behavioral* change to the stall
  detector and the mandate forbids behavior/threading-model changes without runtime verification.
  Recommended follow-up (runtime-verified): relocate `receiveBytesBefore` into `SpiderWorker`,
  reset per page, and confirm stall detection still cancels truly-stuck downloads on a device.

### B8 — `notifyFinish()` can fire more than once  *(LOW)*
- **Where:** called from the last worker at `:1731` (when `mWorkerCount <= 0`), from the Queen
  `run()` at `:1029`, and from the `ensureWorkers` OOM path at `:611`.
- **Status:** not a data race (each call is under its own lock and iterates listeners under
  `mSpiderListeners`), but listeners can receive `onFinish` multiple times. Listeners look
  idempotent today; flagged so a future listener doesn't assume single-shot. No change.

---

## Tests added this round (safety net, no behavior change)

- `app/src/test/java/com/hippo/ehviewer/spider/SpiderInfoTest.java` — round-trip + corrupted-input
  characterization for the `.ehviewer` format: identity/counts/tokens preserved, hex startPage,
  negative-startPage clamp, dropping of `failed`/empty tokens, version rejection, page-count bounds
  (0 and > `MAX_SPIDER_INFO_PAGES`), truncated-file EOF handling, overlong-token rejection,
  separator-less line tolerance, and a 1000-entry pToken map. Pins the contract for the shared
  `pTokenMap` before any concurrency refactor touches its lifecycle (B2).
- `app/src/test/java/com/hippo/ehviewer/spider/SpiderQueenHelpersTest.java` — pure helpers used in
  the concurrent path: `SpiderQueen.contain(int[], int)` (decode de-dup, incl. the `-1`
  INVALID_INDEX sentinel) and `SpiderDen.generateImageFilename` (1-based, zero-padded, locale-
  invariant page→filename mapping shared across workers).
