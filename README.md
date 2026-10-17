# XuaneyNote PR-A verified debug APK download

**APK bytes have been recovered and cryptographically verified.** The original 11,023,697-byte Android APK couldn't be written through the current GitHub connector as one large blob, so it is stored in 11 Git objects under `parts/`. These represent **the same** signed APK produced by [CI Run 38995733428](https://github.com/lihuihnu/XuaneyNote/actions/runs/38995733428), not a rebuild.

## Easy installation preparation

**Windows:** Download the [branch ZIP](https://github.com/lihuihnu/XuaneyNote/archive/refs/heads/testing-apks/pr-a-19532a929475.zip), extract it, then double-click `assemble_test_apk.cmd`. Windows PowerShell will verify each local part, reconstruct the original APK and check the exact whole-file SHA-256. No Python or Android SDK is needed. If you have already extracted the ZIP, no network is required.

**macOS/Linux or Windows with Python 3:** Extract the same ZIP and run `python3 assemble_test_apk.py` (Windows: `py -3 assemble_test_apk.py`). The script can also retrieve missing parts securely from pinned raw GitHub commit `39a0d83a3908c99446bb5b96a4d7b9eb261f4b96`.

**Optional browser method:** Download `download.html`, open it locally in Chrome/Edge with Internet access, and click “验证并下载 APK”. It downloads only from pinned GitHub raw files, validates every part and the complete SHA-256 in the browser, and prompts the APK download. Local-file browser/CORS restrictions may prevent this on some devices; use the Windows/Python method if so. Opening the HTML source preview on github.com does not run the page.

Once created, the file is `XuaneyNote-0.1.0-pr-a-19532a929475-debug.apk`. **APK SHA-256 (must match before installation)**:

```text
9def70c30bed6953a02151ef217aa6935f9948094298c78fc186bd823038f170
```

- Source SHA: `19532a929475e9ce5e9f7fb2589654fb67f101d1`; tested merge SHA: `3f049fd689016b0766f25018db541b2158b31da2`.
- Android `applicationId`: `io.github.lihuihnu.xuaneynote.debug`.
- Version: `0.1.0-pr-a-debug`; `versionCode=1`; Android minSdk 26, targetSdk 36.
- **Temporary CI debug signing certificate SHA-256:** `c8e2c35d3f7dc5f8867485baca46eb2a9f0efff6671b72f0b6357b7104c72ee4`. APK v2 signature verified using Android build-tools `apksigner` during CI. This is NOT a stable upgradeable production signing key; don't uninstall a version holding the only copy of valuable notes.
- The CI-tested phone/tablet are API 35 emulator screen profiles. Physical phone/tablet interaction, SAF file export/import, Chinese IME and TalkBack still require acceptance.
- The parts' exact SHA-256 values are in `manifest.json` and `checksums.sha256`. Do not install a part or bypass the combined SHA-256 check.
- No Actions Artifact, repository write token in ordinary PR CI, uploaded signing key, or official V1 release was used. Only the independent testing branch contains the APK data.

**Important:** This is a test package for disposable notes, not a formal app release. PR #1 remains Draft. The release/verification protocol is [documented in the development branch](https://github.com/lihuihnu/XuaneyNote/blob/19532a929475e9ce5e9f7fb2589654fb67f101d1/docs/TRUSTED_TEST_APK_HANDOFF.md).
