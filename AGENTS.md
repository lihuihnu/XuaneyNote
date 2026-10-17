# XuaneyNote 开发执行约定

先读 `docs/PROJECT_PLAN.md`、`docs/PR_A.md` 和 `docs/CI_EVIDENCE.md`。初始化规划原文保留；当前实现状态以 README 和 PR-A 验收表为准。时间统一 Asia/Tokyo，与初始化规划基线保持一致。

只推进一个主线活动开发 PR；开工前核对远端 main、活动 PR、HEAD。功能修改不直推 main。只认可最终源码与实际 tested SHA 的证据，未运行不能写成通过；不导入其他私有项目的代码、配置、地址或数据。

本项目只用标准 GitHub-hosted `ubuntu-24.04`；不继承自托管 Runner 或故障回退。一个 PR 更新只触发一次普通入口。文档修改走轻量路径。长 Runner job 确认 run ID、SHA 和运行入口后结束当轮，由用户监控并告知结果，不持续轮询。

应用完全本地、免费、免登录，无 INTERNET/ACCESS_NETWORK_STATE、云备份、埋点或广告。数据不能被破坏性迁移、恢复覆盖、错误的成功提示或假样例污染。正文和数据库操作不得放在 UI 线程；必要中文注释，避免过度模块化。

普通 CI 只读、无签名密钥，无 Actions Artifact 和持久缓存。证据是数据而非可执行脚本；维护者先核验身份与容量，再写独立 evidence 引用并远端读回。禁止给 fork 或普通构建放开写权限、禁止 force-push 证据。清理仅限本 job 的已知临时目录，不动用户资料、历史证据或签名密钥。

PR-A 合并前必须取回真实生成的官方 Gradle Wrapper、Room schema，完成成功/受控失败证据入库演练，以及手机/平板窗口、重启与备份验收。真机性能、IME、大字体、TalkBack 未验证时明确标记待验收。
