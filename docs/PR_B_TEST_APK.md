# PR-B 一次性测试 APK 交付与真机 SAF 验收门禁

**目的**：为了人工验证已通过 B3.3 模拟器测试的 XuaneyNote 原生 App，交付可安装于**专门测试环境**的 Debug APK。不是正式版本，不包含稳定的升级签名。

## 单次传输权限边界

- 仅复用现有 `.github/workflows/ci.yml` 官方 GitHub-hosted `ubuntu-24.04` 全套 Android CI，不另建重复 workflow。
- 只有用户本人的同仓库 PR #2、`feat/pr-b-data-safety` → `main`、事件执行账号 `lihuihnu`、PR 作者本人、且带有 **`trusted-test-apk-once`** 标签时，才在合成设备门禁后传输调试 APK。普通 PR 不会传输 APK。
- 只读 CI `contents: read`，使用临时 Debug 证书；绝无 private key/发布证书、GitHub-hosted 外的 Runner、Actions Artifact 或仓库构建写入权限。
- 受信任交付使用同仓库已审阅的 `tools/trusted_apk_transport.py` 与独立反例测试，必须核验实际 APK `aapt`、`apksigner`、完整 source/tested SHA、Run/attempt、传输 SHA-256 和 ZIP CRC。
- 本轮打包标识：`applicationId=io.github.lihuihnu.xuaneynote.debug`、`versionCode=2`、`versionName=0.2.0-pr-b-debug`、`minSdk=26`、`targetSdk=36`。版本名只是测试标识，不代表正式发行。
- Job 完成后由维护者解码校验，独立创建 `testing-apks/pr-b-<source前缀>` 分支的**有界分块**和重组脚本、SHA 校验/签名指纹，并远端读回全部内容，最后才为用户提供受信任下载/安装方式。完成后立即移除一次性 PR 标签。

## 必须如实告知的限制

- 每次 GitHub 官方 Runner 生成的 Debug 签名证书可能不同。即使版本号增加，也**不能**在签名不同的 PR-A Debug APK 上直接覆盖升级；Android 会拒绝。不得通过卸载唯一笔记副本来解决签名不匹配。
- 仅在备用设备、独立 Android 工作资料容器或能安全清空的合成数据安装环境中测试；真实笔记须先独立导出并验证，不能将未加密 ZIP 置于不受信任的第三方位置。
- B3.3 `ImageUiGate.USER_ENTRY_ENABLED=false`，**本测试 APK 只会显示已恢复图片的只读预览**，不能进行新增/移除。先用 [B4.2b 的真实手机 SAF 清单](B4_2B_SAF_ACCEPTANCE.md) 验证完整 ZIP 导出/导入、确认、取消和旧 JSON v1。
- 通过真实 SAF 人工验收后另设计**独立隔离的图片编辑预览版本**验证 Photo Picker，再决定能否修改默认功能闸门；不能把 PR-B Debug APK 视作已开放图片编辑。
- PR #2 继续为 Draft，不合并 main、不视作正式 V1、不使用尚未实现的 B5 密码加密。

## 本次一次性运行目标

- [ ] 对带标签的 PR #2 最新 commit 运行并通过 Android 官方 CI。
- [ ] APK 传输步骤成功发出真实大小、hash、版本与签名指纹，原始 job 日志具备完整块序列。
- [ ] 用户报告该次 CI 结束后，维护者审核并归档可验证的安装包，不假造下载链接。
- [ ] 用户实机依 [SAF 验收清单](B4_2B_SAF_ACCEPTANCE.md) 完成安全人工步骤，再讨论开放图片编辑。
