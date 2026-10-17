# PR-A：受信任的一次性测试 APK 交付

状态：仅启用只读构建和一次性传输门禁。**尚无可下载 APK**，也不是公开正式发布。

## 目标与安全约束

- 经维护者明确要求，使用**官方 GitHub-hosted `ubuntu-24.04`**，从 PR #1 的最新源码构建 `:app:assembleDebug` 并重跑完整 PR-A 自动化门禁。
- 仅在同仓库 PR `feat/pr-a-local-notes`、所有者和触发者均为 `lihuihnu`、且带 `trusted-test-apk-once` 标签时，执行单次测试包传输步骤。标签不是普通 PR 的功能开关；完成交付后移除，之后普通 PR CI 不传 APK。
- CI 保持 `permissions: contents: read`，不使用私有发布密钥、不授予普通 PR 写权限、不使用 `actions/upload-artifact` / `download-artifact`、不把 APK 放进正常源码分支。
- 整体 CI 成功且 `apksigner verify --verbose --print-certs` 通过后，校验 `aapt dump badging` 的 `applicationId=io.github.lihuihnu.xuaneynote.debug`、版本 `0.1.0-pr-a-debug`、`versionCode=1`、`minSdk=26`、`targetSdk=36`，提取签名证书 SHA-256。随后通过 `tools/trusted_apk_transport.py` 在 job 日志中输出有界压缩分块；这只是**临时传输**，不是可安装包的交付链接。
- 维护者事后必须核验 GitHub `run_id/run_attempt`、`source_sha/tested_sha`、完整分块、解压后的 APK SHA-256、ZIP 完整性、包名版本与签名证书 SHA-256；**任何不一致均不得发布**。
- 最后只由受信任维护者通过 GitHub 连接写到单独的 `testing-apks/pr-a-<sourceSHA>` 分支，附 `README.md`、`manifest.json`、`checksums.sha256`，从远端读回一致后才给用户 GitHub 原始文件的下载链接。一次测试包约 11 MB，对整个 Git 历史体积计费，只保留经验证的一份；不上正式 `main`。
- 后续如具备默认分支上的受信任手动发布器，可改为 `workflow_dispatch` + GitHub Releases（不使用 Actions Artifact）。**本次不绕过 PR-A 合并门禁去修改主分支，也不把 PR 构建令牌升级成写权限。**

## 调试签名与升级限制

该 `debug` APK 使用官方 Runner 在该次构建生成的**临时调试证书**；证书 SHA-256 以实际签名报告为准，不预填假的证书值。此证书不是发布证书、不保证后续 CI 的签名相同。若同包名已安装其他测试 APK，Android 可能因签名不一致拒绝覆盖。不要卸载含真实资料的唯一副本；测试资料先通过应用界面备份到独立文件后再升级、卸载或重新安装。正式更新能力须另设受保护的发布签名流程，不能存私钥到仓库。

APK 是用来**验证 PR-A** 的一次性测试包，不是正式 V1，不包含付费/账户/联网功能，也不表示实体设备已验收。签名校验只能证明构建身份和字节完整性，不表示 APK 获得 Google Play 等平台认证。

## 维护者解包命令

```bash
python3 -S tools/trusted_apk_transport.py unpack job.log output-apk \
  --source-sha <完整40位PR源码SHA> \
  --tested-sha <完整40位CI checkout的merge SHA> \
  --run-id <实际GitHub Run编号> --run-attempt 1
```

拒绝超限、缺块、重复块、顺序损坏、SHA-256 不符、错误身份和不符合包名/签名约束的载荷。验收完成后将实际包 SHA-256、签名证书 SHA-256 和可点击下载链接记录在 PR 评论及本次发行分支，不改写此前的 CI 证据。

验收操作见 [REAL_DEVICE_ACCEPTANCE.md](REAL_DEVICE_ACCEPTANCE.md)。
