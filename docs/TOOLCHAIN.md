# PR-A 固定工具链

选用已发布、兼容关系明确的固定组合，不宣称它们是 2026-10-17 的所有最新版本，也不在未经 CI 前宣称联合构建已通过。

| 项目 | 固定值 |
| --- | --- |
| AGP | 8.13.2 |
| Gradle | 8.13 |
| JDK | 17 / Temurin in CI |
| Kotlin / Compose compiler plugin | 2.2.10 / 2.2.10 |
| KSP | 2.2.10-2.0.2 |
| Compose BOM | 2025.08.01 |
| Room | 2.8.4 |
| DataStore | 1.1.7 |
| Lifecycle / Activity | 2.9.4 / 1.10.1 |
| Coroutines | 1.10.2 |
| min / compile / target SDK | 26 / 36 / 36 |
| Build Tools | 35.0.0（AGP 默认） |
| 模拟器测试镜像 | API 35 / google_apis / x86_64 |

Gradle 发行包 SHA-256：`20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`。引导脚本下载后先校验再使用；首次 CI 生成官方 Wrapper，校验后审阅提交。在此之前通过 `bash tools/gradle.sh ...` 构建；Android Studio 可以选择引导脚本生成的 `.bootstrap/gradle-8.13` 本地安装。首次构建需要下载开发依赖，但打包应用运行时不联网。

默认无持久 Actions 缓存、无 NDK、无 C++、无 KAPT。每 job 复用已下载依赖和 Gradle up-to-date 输出；不以并行创建大量空模块换取架构形式。JUnit、Lint、模拟器结果分别记录，不能用本地 Kotlin 1.9/JDK21 补充烟雾检查替代本表环境。

升级版本通过同一开发 PR 逐项验证，不能使用 `+` 或 SNAPSHOT。首次完整构建后审阅依赖报告、Room schema、官方 Wrapper，再决定是否增加依赖验证元数据；当前未声称所有 Maven 传递依赖都已完成独立校验。

## 官方依据

- AGP 8.13 兼容性： https://developer.android.com/build/releases/agp-8-13-0-release-notes
- Gradle 发行包与 Wrapper 校验： https://gradle.org/release-checksums/
- Compose BOM 映射： https://developer.android.com/develop/ui/compose/bom/bom-mapping
- Room： https://developer.android.com/jetpack/androidx/releases/room
- DataStore： https://developer.android.com/jetpack/androidx/releases/datastore
- Compose 编译器： https://developer.android.com/jetpack/androidx/releases/compose-kotlin
- 第三方组件的许可须在正式发布前随依赖清单审阅；项目自身 LICENSE 仍待维护者确认。
