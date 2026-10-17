# XuaneyNote

**专注当下，记录生活。** 面向 Android 手机和平板的完全本地、免费、无需登录的记录与专注应用。

> 当前为 **PR-A 开发预览**：官方 Run #3（`38988757189`）已在 Android 15 模拟器上通过编译、Lint、Room、手机/平板布局与独立进程重启。实际 Gradle Wrapper 与 Room v1 schema 已从本次日志校验取回，并按相同字节提交。本次回填后的最终源码 SHA 仍需重新 CI，实体设备与 SAF 文件选择器完整手工往返仍待验收；不是 V1 发布包，也不建议作为重要资料的唯一副本。

## PR-A 范围

原生 Kotlin / Compose / Material 3；手机单栏、宽窗口列表—编辑双栏；Room 文本笔记；串行自动保存与明确保存状态；退出重开找回已提交内容；系统文件选择器导出、校验与冲突保留式恢复；DataStore 明暗主题。

暂不开放的番茄钟、日记、手账、分类、搜索和回收站不以假数据或假实现代替。没有账号、服务器、联网权限、广告或埋点；首次启动为空库。

## 文档入口

- [完整项目目标与规划基线](docs/PROJECT_PLAN.md)：原初始化 README 原文保留。
- [PR-A 范围、架构与验收清单](docs/PR_A.md)。
- [首轮失败复核](docs/CI_RUN_38939329504.md)和[Run #3 成功证据](docs/CI_RUN_38988757189.md)。
- [文本备份 v1 合同](docs/BACKUP_V1.md)。
- [官方 Runner、证据交付和清理](docs/CI_EVIDENCE.md)。
- [固定工具链及构建说明](docs/TOOLCHAIN.md)。
- [测试 APK 的受信任交付流程](docs/TRUSTED_TEST_APK_HANDOFF.md)与[真实设备验收清单](docs/REAL_DEVICE_ACCEPTANCE.md)。
- [Agent 执行约定](AGENTS.md)。

## 开发构建

初始依赖：JDK 17、Android SDK 36、Build Tools 35.0.0。支持目标为 Android 8.0 / API 26 及以上；实际兼容性以设备门禁为准。

```sh
# Linux / macOS；SDK 路径通过 ANDROID_HOME 或未提交的 local.properties 配置。
./gradlew --no-daemon :core:model:test :app:assembleDebug
```

`gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar` 和 `gradle-wrapper.properties` 均来自 Run #3 实际生成文件；JAR 和发行包 SHA-256 已核实。正常开发与 CI 使用**官方 Gradle Wrapper**。原有 `tools/gradle.sh` 仅保留作经过 SHA-256 校验的应急引导，不是首选构建路径。Room v1 schema 同样来自该轮 KSP 实际输出。

Debug applicationId 带 `.debug` 后缀，与将来的正式资料库隔离；普通 PR 不接触正式签名密钥。构建出的临时 APK 用于模拟器测试，不等同于已发布、可覆盖升级的安装包。

## 当前验证边界

Run #3：源码 `06ae6fc4...` / tested merge `ed076740...`，官方 `ubuntu-24.04` 完整 CI 为 **success**；模型 JUnit 9/9、Room/备份 4/4、手机 Compose 2/2、平板 Compose 2/2，手机与平板分别 `seed → force-stop → verify` 通过。最终合并 Manifest 无联网权限。原始日志、XML、合成截图、Gradle Wrapper、Room schema 共 36 个载荷文件已校验并入库，见 [Run #3 说明](docs/CI_RUN_38988757189.md)。

此次回填了 Wrapper/schema，并改为通过官方 `./gradlew` 构建；**回填后的新 HEAD 尚需一轮真实 CI**，不能以旧 SHA 通过替代新 SHA。API 35 模拟器不等于实体手机/平板，系统文件选择器 SAF 完整往返、真实中文输入法、大字体、TalkBack 与性能仍待实机验收。普通 PR 不生成可下载正式 APK。

CI 只有一条官方 Runner 入口、只读权限；无 Artifact、持久缓存、重复 push 触发或自托管 Runner。CI 执行、验证通过、证据入库、合并和发布是独立状态。

## 许可

免费使用目标不变。项目源代码许可证仍待维护者决定；公开仓库不等同于已经授予任意再分发许可。第三方组件遵守各自许可证，正式发行前审阅完整依赖和资源清单。
