# XuaneyNote 开发执行约定

先读 `docs/PROJECT_PLAN.md`、`docs/PR_B.md`、`docs/BACKUP_V1.md`、`docs/PR_A.md` 和 `docs/CI_EVIDENCE.md`。原始规划基线不改写；当前实现状态以 README、最新活动 PR 和代码为准。时间统一 Asia/Tokyo，与初始化规划基线保持一致。

只推进一个主线活动开发 PR；开工前核对远端 main、活动 PR、HEAD。功能修改不直推 main。只认可最终源码与实际 tested SHA 的证据，未运行不能写成通过；不导入其他私有项目的代码、配置、地址或数据。

本项目只用标准 GitHub-hosted `ubuntu-24.04`；不继承自托管 Runner 或故障回退。一个 PR 更新只触发一次普通入口。文档修改走轻量路径。长 Runner job 确认 run ID、SHA 和运行入口后结束当轮，由用户监控并告知结果，不持续轮询。

应用完全本地、免费、免登录，无 INTERNET/ACCESS_NETWORK_STATE、云备份、埋点或广告。数据不能被破坏性迁移、恢复覆盖、错误的成功提示或假样例污染。正文和数据库操作不得放在 UI 线程；必要中文注释，避免过度模块化。

普通 CI 只读、无签名密钥，无 Actions Artifact 和持久缓存。证据是数据而非可执行脚本；维护者先核验身份与容量，再写独立 evidence 引用并远端读回。禁止给 fork 或普通构建放开写权限、禁止 force-push 证据。清理仅限本 job 的已知临时目录，不动用户资料、历史证据或签名密钥。

PR-A 已在 2026-10-17 合并为 `b85e27d5c004f978850badc4865dea821c0b44d1`；用户报告手机验收无异常，未提交逐项真机性能、独立平板等记录的测试不可冒称通过。PR-B 活动分支为 `feat/pr-b-data-safety`，冻结目标见 `docs/PR_B.md`：先 v1→v2 显式迁移，再做搜索、回收站、图片附件、完整 v2 备份与密码保护。新增实体必须同一阶段扩展备份；绝对不能破坏旧 v1 数据、旧备份、自动保存及离线隐私边界。所有阶段以实际 Run/source/tested SHA 的 CI 证据为准。B0/B1/B2/B3.1/B3.2/B4.1/B4.2a/B4.2b 均取得真实官方 CI 并归档；维护者于 2026-10-17 报告手机 SAF 验收通过（暂缺机型、逐项证据及同签名升级）；B3.3 只读预览已在 Run 39034356792 通过官方 CI。下一阶段只允许 Debug-only Photo Picker 专项，Release 的 ImageUiGate 仍强制关闭；新测试 APK 继续走官方 Runner 的所有者+同仓库 PR+一次性标签门禁，维护者已于 2026-10-18 报告 B3.4 图片真机验收通过，但未提供逐项记录或同签名升级证据；Release 图片编辑门禁不变。B5.0 安全评审参见 docs/B5_ENCRYPTED_BACKUP_SECURITY_REVIEW.md：先审阅 Zip4j 2.11.6 的许可证、CVE、密码字符编码、API26 及第三方解压互操作，明确 PBKDF2 1000 次和 ZIP 元数据泄漏；在互操作与设备验证前，不把候选库加入生产 Gradle、不宣布加密可用。B5 编码与新 CI 循序进行，PR-B 继续 Draft。
