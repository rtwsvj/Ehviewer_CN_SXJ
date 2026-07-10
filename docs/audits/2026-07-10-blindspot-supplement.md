# Ehview 审计盲区补充报告

## 状态

- 报告状态：实施中
- 基线：`72ae3beb8ab76b707c611e8895f5d40fb1bff3e6`
- 方法：静态调用路径复核、现有测试矩阵盘点、补充失败回归、故障注入与规模测试。

## 新增盲区结论

### B0-1 破坏性操作缺少“零删除”不变量

现有测试覆盖 manifest/scanner 的正常兼容，却未从文件 hash 层证明清理操作不会删除有效图库。补测必须覆盖 `N 图片 + .ehviewer + manifest.json`、额外元数据、目录项、零字节页和重复执行。

### B0-2 应用锁只测试正向认证，没有测试所有外部入口

门禁测试需要同时覆盖导出 Activity、`onNewIntent`、任意 Scene class、通知 PendingIntent、VIEW/SEND 转发和摇晃传感器路径。

### B0-3 归档安全测试检查了浏览器路径，遗漏下载后 `GZIPUtils` 路径

已有 A7Zip 浏览路径限制不能保护另一条直接落盘解压逻辑。需要独立验证 traversal、重复 normalized path、声明大小欺骗、实际写出超限、压缩比和失败 staging 清理。

### B1-1 Wi-Fi 单帧测试遗漏 TCP 合包、半帧和并发写

当前测试覆盖单帧与 marker 跨 read，但没有两帧同 read、首帧加半个次帧、marker 位于 JSON 字符串、UTF-8 跨 chunk、并发 sender 字节交错和 EOF 半帧。

### B1-2 数据结构测试验证结果，没有验证内部索引不变量

DownloadManager 的 map、all list、label bucket、label count、wait/current task 需要在每个 mutation 后统一验证，且需随机化 Wi-Fi 消息到达顺序。

### B1-3 本地库测试规模过小，无法暴露主线程 I/O、N+1 和写放大

除正确性 fixture 外，需要 100/1,000/10,000 作品规模、慢 provider、query/write/listFiles counter、事务失败和进程中断测试。

### B1-4 绿 CI 统计 test case，而非生产代码覆盖率

当前“coverage”步骤不采集行或分支覆盖率。应先建立 first-party 基线，再对本次高风险包设置 changed-line ratchet，并用 mutation 验证测试能捕获回归。

### B2-1 native 解析器只有上游语料，没有本项目调用边界 fuzz

需要以 App 实际 JNI 入口和 archive/image provider 为 harness，覆盖截断、超大尺寸、整数边界和多格式伪装；在 harness 建立前不得声称 native 风险已关闭。

## 实施优先级

1. 先让 P0/B0 测试在基线稳定失败并立即修复。
2. 再关闭归档、WebView、Wi-Fi 和 FileProvider 的外部输入面。
3. 随后用不变量、故障注入和规模测试推进状态/性能修复。
4. 最后建立供应链、覆盖率和 native fuzz 的长期门禁。

## 不可由本地测试替代的外部验证

- 已泄露凭据的服务端吊销与轮换。
- 远端 Git 历史重写和 fork 协调。
- 真实账号的服务端会话语义。
- 新 Wi-Fi 加密协议的跨版本互操作与密码学评审。

