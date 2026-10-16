# XuaneyNote

**专注当下，记录生活。** 面向 Android 手机和平板的完全本地、免费、无需登录的记录与专注应用。

> 当前为 **PR-A 开发预览**：原生笔记闭环与最小 CI 源码已加入，尚待锁定工具链的 Android 编译、模拟器及真机验收；不是已发布的 V1。不要作为重要资料的唯一副本。

## PR-A 范围

原生 Kotlin / Compose / Material 3；手机单栏、宽窗口列表—编辑双栏；Room 文本笔记；串行自动保存与明确保存状态；退出重开找回已提交内容；系统文件选择器导出、校验与冲突保留式恢复；DataStore 明暗主题。

暂不开放的番茄钟、日记、手账、分类、搜索和回收站不以假数据或假实现代替。没有账号、服务器、联网权限、广告或埋点；首次启动为空库。

## 文档入口

- [完整项目目标与规划基线](docs/PROJECT_PLAN.md)：原初始化 README 原文保留。
- [PR-A 范围、架构与验收清单](docs/PR_A.md)。
- [文本备份 v1 合同](docs/BACKUP_V1.md)。
- [官方 Runner、证据交付和清理](docs/CI_EVIDENCE.md)。
- [固定工具链及构建说明](docs/TOOLCHAIN.md)。
- [Agent 执行约定](AGENTS.md)。

## 开发构建

初始依赖：JDK 17、Android SDK 36、Build Tools 35.0.0。支持目标为 Android 8.0 / API 26 及以上；实际兼容性以设备门禁为准。

```sh
# Linux / macOS；SDK 路径通过 ANDROID_HOME 或未提交的 local.properties 配置。
bash tools/gradle.sh --no-daemon :core:model:test :app:assembleDebug
```

`tools/gradle.sh` 是带官方 SHA-256 校验的 Gradle 8.13 发行包引导脚本，**不是伪装的 Gradle Wrapper**。首轮 CI 同时生成官方 Wrapper，把校验后的引导文件和 Room v1 schema 返回证据；审阅并提交这两项后才允许合并 PR-A。Android Studio 在此之前可指定 `.bootstrap/gradle-8.13` 为本地 Gradle 安装。

Debug applicationId 带 `.debug` 后缀，与将来的正式资料库隔离；普通 PR 不接触正式签名密钥。构建出的临时 APK 用于模拟器测试，不等同于已发布、可覆盖升级的安装包。

## 当前验证边界

本地补充 JVM 烟雾检查 11 项通过；证据协议回归 7 项通过；XML、固定版本、权限源文件、工作流策略与 Shell 语法检查通过。补充 JVM 环境并非锁定的 Android 工具链，不可替代 Gradle、Room、Compose 或真机门禁。

本 PR 的 CI 是单一入口，官方 `ubuntu-24.04`，只读权限；无 Artifact、持久缓存、定时任务、重复 push 触发或自托管 Runner。CI 运行、通过、证据入库、合并和发布分别报告。

## 许可

免费使用目标不变。项目源代码许可证仍待维护者决定；公开仓库不等同于已经授予任意再分发许可。第三方组件遵守各自许可证，正式发行前审阅完整依赖和资源清单。
