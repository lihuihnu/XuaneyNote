# XuaneyNote B5.3 — one-time trusted Debug APK archive

This archive contains **11 binary zlib parts**, not a directly installable APK. The complete compressed stream was independently checked against official CI SHA-256 before archiving. Reconstruct offline; the scripts must verify **every part**, the combined compressed SHA-256, the full original signed APK SHA-256, and APK CRC before installation.

- Source SHA: `22778b18ace142cf58e218c0424b4bb332730af5`
- Tested PR merge SHA: `6d6774568194eca1ee46e0b3032cbd11ff9fef04`
- Official GitHub-hosted CI: [Run 39422300056](https://github.com/lihuihnu/XuaneyNote/actions/runs/39422300056), attempt 1, success
- Application package: `io.github.lihuihnu.xuaneynote.debug`
- Version: `0.2.0-pr-b-debug`, minSdk 26, targetSdk 36
- APK size: **11324953** bytes; original signed APK SHA-256: `0f03e6565dc684b4986ca41f9c686622bf3f027e29dc9f7625618abc24447387`
- Original CI Debug certificate SHA-256: `b1b485420a83b14b777349f9e91e2bf5b4b7d2e530df6e55c95e6a2fd6382349`
- Combined compressed stream: **10789582** bytes; SHA-256: `f0df31fabd0b28d3f56eae32f6941a3d7f3e3af013302e179a16e78e8224f90f`
- Parts: 11, listed in `manifest.json` / `checksums.sha256`

## Assemble locally

**Windows**: Extract the GitHub commit ZIP preserving the `parts/` directory, then double-click `assemble_test_apk.cmd` (Windows PowerShell). This checks all hashes and creates the exact Debug APK in the extracted folder. Alternatively run `python assemble_test_apk.py` with Python 3 on Windows, macOS, or Linux.

**Important**: The APK is **temporarily Debug-signed** and may not replace an earlier differently signed APK. Never uninstall a device's only copy of important notes just to install this build. Use disposable synthetic notes or an independent test environment. B5.3 password backup is a new feature under device acceptance, **not a released security guarantee**. WinZip AES KDF is relatively weak; choose long high-entropy passwords. Do not assume every external ZIP app accepts Unicode passwords.

Use [the B5.3 SAF physical-device checklist](https://github.com/lihuihnu/XuaneyNote/blob/feat/pr-b-data-safety/docs/B5_3_SAF_ACCEPTANCE.md) and report actual device model, Android version, provider, and what passed/failed. PR #2 stays Draft until that review.
