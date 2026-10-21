# XuaneyNote 开发执行约定

先读 `docs/PROJECT_PLAN.md`、`docs/PR_B.md`、`docs/BACKUP_V1.md`、`docs/PR_A.md` 和 `docs/CI_EVIDENCE.md`。原始规划基线不改写；当前实现状态以 README、最新活动 PR 和代码为准。时间统一 Asia/Tokyo，与初始化规划基线保持一致。

只推进一个主线活动开发 PR；开工前核对远端 main、活动 PR、HEAD。功能修改不直推 main。只认可最终源码与实际 tested SHA 的证据，未运行不能写成通过；不导入其他私有项目的代码、配置、地址或数据。

本项目只用标准 GitHub-hosted `ubuntu-24.04`；不继承自托管 Runner 或故障回退。一个 PR 更新只触发一次普通入口。文档修改走轻量路径。长 Runner job 确认 run ID、SHA 和运行入口后结束当轮，由用户监控并告知结果，不持续轮询。

应用完全本地、免费、免登录，无 INTERNET/ACCESS_NETWORK_STATE、云备份、埋点或广告。数据不能被破坏性迁移、恢复覆盖、错误的成功提示或假样例污染。正文和数据库操作不得放在 UI 线程；必要中文注释，避免过度模块化。

普通 CI 只读、无签名密钥，无 Actions Artifact 和持久缓存。证据是数据而非可执行脚本；维护者先核验身份与容量，再写独立 evidence 引用并远端读回。禁止给 fork 或普通构建放开写权限、禁止 force-push 证据。清理仅限本 job 的已知临时目录，不动用户资料、历史证据或签名密钥。

PR-A 已于 2026-10-17 合并。PR-B #2 于 2026-10-21 按维护者指令集成为 main `f513b8a`，其最终 HEAD `22778b18` 的官方 Run 39422300056 成功；但 B5.3 真实手机加密 SAF/异常 provider/同签名升级仍待验收，不得将合并冒充正式发布。AES ZIP 弱 PBKDF2 KDF 风险与 Release 图片编辑门禁不得解除。

当前活动 PR-C0 分支 `feat/pr-c0-focus-timer`。只做单调时钟计时状态机、独立本地临时状态及手机/平板专注页；不加入通知、历史、任务、数据迁移或新权限，参见 `docs/PR_C0.md`。C0 使用专门的轻量 Kotlin/JVM 单元测试 + API35 手机/平板聚焦 Compose smoke（单模拟器），不重复触发 B5 AES 跨工具/Room 全量/API26 矩阵；通过后需独立真实手机熄屏、重启与系统时间变化验收。仍仅官方 ubuntu-24.04 Runner，禁止自行轮询长 CI。
