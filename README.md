# XuaneyNote

**专注当下，记录生活。** 面向 Android 手机和平板的完全本地、免费、无需登录的笔记、日记、手账、备忘录与番茄专注应用。

> **当前状态（2026-10-17）**：PR-A [#1](https://github.com/lihuihnu/XuaneyNote/pull/1) 已合并至 `main`（`b85e27d5c004f978850badc4865dea821c0b44d1`），交付最小原生笔记闭环。PR-B 数据安全与资料管理已在 `feat/pr-b-data-safety` **冻结目标，B0 已完成最终 CI 验收；B1 中文搜索已通过官方 CI，B2 回收站已通过官方 CI；B3.1/B3.2 内部数据与文件门禁已通过；B4.1 ZIP v2 校验编解码待 CI，仍不开放用户插图，B4–B5 尚未开始**。这仍不是正式 V1 发布。

## 已实现：PR-A 基础笔记

- Kotlin / Jetpack Compose / Material 3，手机单栏、达到 840dp 的宽窗口列表—编辑双栏，明暗主题；
- Room 本地文本笔记、自动保存状态、进程终止后重开找回，空库而非演示内容；
- 系统文件选择器（SAF）手动导出/验证与冲突保留式 JSON v1 恢复；此格式**未加密，且不支持附件**；
- 完全离线、免登录，没有应用运行时 INTERNET / ACCESS_NETWORK_STATE、广告或遥测。

## 开发中：PR-B 冻结目标

[**PR-B 目标与数据合同（docs/PR_B.md）**](docs/PR_B.md) 固定六项闭环：Room v1→v2 显式安全迁移、中文/英文搜索、回收站、私有图片附件、包含附件的备份 v2、可选密码保护的加密备份。**B0 [Run 39001250626](https://github.com/lihuihnu/XuaneyNote/actions/runs/39001250626)、B1 [Run 39002603730](https://github.com/lihuihnu/XuaneyNote/actions/runs/39002603730) 已通过；B2 [Run 39005974577](https://github.com/lihuihnu/XuaneyNote/actions/runs/39005974577) 已通过；B3.1 最终 [Run 39009835439](https://github.com/lihuihnu/XuaneyNote/actions/runs/39009835439) success；B3.2 [Run 39012222967](https://github.com/lihuihnu/XuaneyNote/actions/runs/39012222967) 已通过；B4.1 ZIP v2 编解码/预检候选待新源码 CI，真实恢复和图片选择界面尚未开放。** 见 [B1 搜索](docs/B1_SEARCH.md)、[B2 回收站](docs/B2_TRASH.md)、[B3 图片](docs/B3_ATTACHMENTS.md) 和 [B4 ZIP 备份合同](docs/B4_BUNDLE_V2.md)。

计划顺序：B0 数据迁移 → B1 搜索 → B2 回收站 → B3 图片 → B4 完整备份 → B5 密码备份。每一步单独补齐验证，最后以最新源码 SHA 的官方 CI 与手工文件选择器回归验收。此阶段不提前开发番茄钟、日记或手账画布。

## 最新 PR-A 验证记录与边界

- 官方标准 GitHub-hosted `ubuntu-24.04` 的 [Run 38995733428](https://github.com/lihuihnu/XuaneyNote/actions/runs/38995733428) 为 **completed/success**；源码 `19532a929475e9ce5e9f7fb2589654fb67f101d1`，实际 tested merge `3f049fd689016b0766f25018db541b2158b31da2`。构建、Lint、Room、API35 模拟手机/平板窗口与进程重启测试通过；最终合并 Manifest 隐私检查通过。
- 用户反馈：已完成**手机实机验收、没有发现问题**。未附具体型号、逐项录屏、独立实体平板验收和可量化性能证据，故不冒称这些项目全部通过。
- 一次性测试 APK 已归档为**校验后可拼接的分片**，独立于 `main`；查看 [测试包 README](https://github.com/lihuihnu/XuaneyNote/tree/testing-apks/pr-a-19532a929475)。临时 CI 调试证书不等于正式可更新签名；不要用该调试版承载资料唯一副本。
- 官方 Wrapper、Room v1 schema 和成功/受控失败 CI 证据已按原始 SHA 固定；具体历史见 [PR-A 归档](docs/PR_A.md) 与 [CI 证据规范](docs/CI_EVIDENCE.md)。

## 文档导航

- [项目完整规划基线](docs/PROJECT_PLAN.md)
- [PR-B 目标冻结及全部验收门禁](docs/PR_B.md)
- [PR-A 历史与合并记录](docs/PR_A.md)
- [JSON 文本备份 v1 合同](docs/BACKUP_V1.md)
- [CI 证据协议](docs/CI_EVIDENCE.md) / [工具链](docs/TOOLCHAIN.md)
- [测试 APK 交付](docs/TRUSTED_TEST_APK_HANDOFF.md) / [手机验收清单](docs/REAL_DEVICE_ACCEPTANCE.md)
- [Agent 执行约定](AGENTS.md)

## 本地构建

初始要求：JDK 17、Android SDK 36、Build Tools 35.0.0；`minSdk 26`。实际 Gradle Wrapper 和 Room v1 schema 均经首轮官方 CI 生成文件字节身份校验。

```sh
./gradlew --no-daemon :core:model:test :app:assembleDebug
```

调试包使用 `.debug` 的独立 applicationId；本仓库普通 PR CI 只读、仅官方 `ubuntu-24.04`、无 Actions Artifact/持久缓存，也不触发正式签名。

## 许可与发布

免费使用目标不变；源码许可证仍待维护者确认。**公开仓库不等于正式发行**，不擅自承诺分发许可、签名升级或未经测量的跨设备性能。
