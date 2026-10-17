# XuaneyNote PR-A CI Run 38988757189 / attempt 1

- **完整结论**：GitHub-hosted `ubuntu-24.04` 官方 Runner 的 workflow/job 均为 `success`。
- 源码 SHA：`06ae6fc4b49a2c8f7b5b854e94b7e6de54251e37`；实际测试的 PR merge SHA：`ed076740e734641c1e0d3b6870b47714a461a208`。不要将两者混同。
- runner：`GitHub Actions 1000032147`；镜像：`20261012.334.1`。
- 模型单元测试 **9/9**；Room/备份合同 **4/4**；手机 Compose **2/2**；平板 Compose **2/2**，均无失败。
- 手机和平板分别执行了 `seed → adb force-stop → verify`，对应日志 `logs/phone-restart-*.log` 和 `logs/tablet-restart-*.log`，各步骤 `OK (1 test)`。
- 通过了 Lint、Debug APK 构建、最终合并 Manifest 无网络权限检查、官方 Wrapper 生成和 CI 证据协议校验。
- 手机与平板使用同一 API 35 Android 15 模拟器改变分辨率/密度；**不是实体设备**。SAF 文件选择器完整手工往返、大字体、TalkBack、中文 IME 与真机性能仍需独立验收。
- APK 仅在 Runner 用于测试，已清理，**没有发布 APK**；测试包 SHA-256 在 `manifest.json` 中。
- 本次从 job 日志的 157 块压缩原始封装恢复 36 个文件（共 349655 字节），先核对整个封装 SHA-256 `e697c2cc1a6864896ecc61305aee1ef3cbcd2b32032419b4911a55d5e3676897`，再逐文件核对 SHA-256；没有文件缺失或截断。
- 官方 Gradle Wrapper JAR 原始 SHA-256：`81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`，与项目审定值一致。Room v1 schema 来自本次实际 KSP 输出，不手填 identityHash。

`manifest.json` 保留 CI 原始 `archive_status=transported-not-yet-repository-verified` 字段，反映 CI job 运行时的状态；该证据分支的独立仓库读回核验另行执行，不得把 job 阶段的声明改写成已入库。
