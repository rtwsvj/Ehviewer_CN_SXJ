# Codex 工程记录

本目录用于保存可由后续工程师和审计代理复核的执行证据。

- `execution-reports/`：修改前的目标、范围、风险、回滚与验证计划。
- `change-records/`：逐项声明实际修改、文件和可核验证据。
- `verification-reports/`：测试、构建、对抗检查与未验证边界。

每次实质修改至少应有一份执行报告和一份改动记录。改动记录中的每一项完成声明都必须能从代码、Git diff 或验证报告中独立核对。

- `session-log.md`：按任务串联执行报告、改动记录、验证报告和最终审计，避免只看单份文档误判完成状态。

## 最新任务

- 执行：`execution-reports/2026-07-10-deep-audit-remediation.md`
- 改动：`change-records/2026-07-10-deep-audit-remediation.md`
- 核验：`verification-reports/2026-07-10-deep-audit-remediation.md`
- 最终审计：`../audits/2026-07-10-remediation-final.md`
