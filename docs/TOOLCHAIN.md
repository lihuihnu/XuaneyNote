# PR-A 固定工具链

选用已发布、兼容关系明确的固定组合。该组合已在官方 Run #3 经实际编译、Lint 和 API 35 模拟器验收，但回填官方 Wrapper/schema 后必须重新验证最终 HEAD。

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

Gradle 8.13 发行包 SHA-256：`20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`；从官方 Run #3 恢复的 Wrapper JAR SHA-256：`81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`。正常使用 `./gradlew --no-daemon ...`；Wrapper 自动校验发行包。`tools/gradle.sh` 只作为备用引导，不再让每次 CI 重新生成 Wrapper。开发首次构建需要下载 SDK/依赖，但打包应用运行时没有联网权限。

默认无持久 Actions 缓存、无 NDK、无 C++、无 KAPT。每 job 复用已下载依赖和 Gradle up-to-date 输出；不以并行创建大量空模块换取架构形式。JUnit、Lint、模拟器结果分别记录，不能用本地 Kotlin 1.9/JDK21 补充烟雾检查替代本表环境。

升级版本通过同一开发 PR 逐项验证，不能使用 `+` 或 SNAPSHOT。首次 CI 已生成并验证 Room schema、官方 Wrapper；后续数据库实体变更必须提交新的迁移和 schema，不能改写 v1 基线。依赖验证元数据如需启用，单独进行；未声称所有 Maven 传递依赖都已完成独立校验。

## 官方依据

- AGP 8.13 兼容性： https://developer.android.com/build/releases/agp-8-13-0-release-notes
- Gradle 发行包与 Wrapper 校验： https://gradle.org/release-checksums/
- Compose BOM 映射： https://developer.android.com/develop/ui/compose/bom/bom-mapping
- Room： https://developer.android.com/jetpack/androidx/releases/room
- DataStore： https://developer.android.com/jetpack/androidx/releases/datastore
- Compose 编译器： https://developer.android.com/jetpack/androidx/releases/compose-kotlin
- 第三方组件的许可须在正式发布前随依赖清单审阅；项目自身 LICENSE 仍待维护者确认。
