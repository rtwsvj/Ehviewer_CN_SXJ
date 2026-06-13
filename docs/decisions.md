# Decisions

## 2026-06-13: RequestGovernor rollout defaults

Eric accepted the staged rollout plan for `RequestGovernor`.

- Keep the current recommended defaults: `1500 ms` minimum request spacing, `30 min` limit-signal cooldown, and `2-16 s` failure backoff.
- Ship with these defaults first. If production use still triggers site rate limits, switch to the conservative profile documented in the overnight report.
- No code change is needed for this decision: `Settings` already hot-reads the governor enable flag, request spacing, cooldown, and failure backoff values at runtime. The download settings UI currently exposes the enable flag, request spacing, and cooldown; the failure backoff base/max are also Settings-backed.
