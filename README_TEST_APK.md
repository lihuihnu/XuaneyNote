# XuaneyNote 0.3.0 PR-C0 — verified one-time Debug APK archive

**This fixed archive contains 12 binary zlib pieces, not a directly installable APK.** The entire compressed stream was independently checked against official CI SHA-256 before archival. Reassemble *offline* using the included scripts; they verify every part, the compressed SHA-256, original APK SHA-256 and APK ZIP CRC. No cloud data, credentials or private notes are embedded.

- Source SHA: `05abc54228880eca4c8e4d2dfa801befa273a633`
- Tested PR merge SHA: `a123f13f6f3c570028b6513f4e13a197263ea0c1`
- Official CI: [Run #42138103656](https://github.com/lihuihnu/XuaneyNote/actions/runs/42138103656), attempt 1, **success**
- Package: `io.github.lihuihnu.xuaneynote.debug`
- Version: `0.3.0-pr-c0-debug`, `versionCode=3`, minSdk 26, targetSdk 36
- APK size: **11365265 bytes**; SHA-256: `5ab90bb2c08d301947c3109f8a1904ebf949aab2d3bbf68a6c86fc500285b4f8`
- CI temporary Debug certificate SHA-256: `3c3d7f109e8f86b5e785f29f81177dda0a071be4e9a4fa7c52c18dda8b02b3a9`
- Compressed zlib: 10822436 bytes; SHA-256: `aebff81b16416a0b4dc735cc03394428bdc606f3b7581a01b34800f955203256`
- 12 parts: verified SHA-256 in `manifest.json` and `checksums.sha256`

## Windows 安装步骤

1. 从 GitHub 下载此固定提交的 ZIP，完整解压，保留 `parts/` 文件夹。
2. 双击 `assemble_test_apk.cmd`（使用 Windows 内置 PowerShell，不需要安装 Python）。
3. 全部校验通过后，目录中生成 `XuaneyNote-0.3.0-pr-c0-05abc5422888-debug.apk`，复制到安卓手机安装。
4. 如在 Linux/macOS 或已安装 Python 3 的 Windows，执行 `python3 assemble_test_apk.py`，其操作为同一套哈希及 ZIP 验证。

## 重要限制

此 APK 由**临时 GitHub Runner Debug 证书**签名，不能保证覆盖其他 CI 运行产生的 APK；不要为了安装它卸载唯一保存重要笔记的旧版。建议使用可清空的独立测试环境、合成笔记。PR-C0 尚未完成真实手机熄屏、后台进程终止和设备重启验收。PR-B 加密备份的真实 SAF 及同签名升级也尚待验收，ZIP AES 的弱 KDF 不适合作为低熵密码的可靠防护。

本归档是开发测试包，不是正式稳定 Release。代码见 [PR #3](https://github.com/lihuihnu/XuaneyNote/pull/3)。
