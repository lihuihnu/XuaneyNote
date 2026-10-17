# XuaneyNote PR-B — verified one-time test APK handoff

**Status:** The official GitHub-hosted run completed successfully. Eleven compressed binary pieces of the original APK have been extracted from the exact trusted log transport, independently SHA-256-verified and stored here as Git objects. This is **not** a one-file APK download or a formal app release.

**Source SHA:** `6614745c341bcd84019a40af2ab57d4ab7ee22b4`. **Tested merge SHA:** `1896431055156954d8c1e2761ef02daa469fb41c`. [CI Run 39035771479](https://github.com/lihuihnu/XuaneyNote/actions/runs/39035771479) passed all required Android checks and executed the owner-only APK transfer step.

## Create the APK without an Android SDK

1. Download the [PR-B test-package ZIP](https://github.com/lihuihnu/XuaneyNote/archive/refs/heads/testing-apks/pr-b-6614745c341b.zip) and extract it on a computer. The 11 binary files in `parts/` must remain present.
2. **Windows:** double-click `assemble_test_apk.cmd`. The bundled PowerShell script verifies each compressed part, reconstructs the original signed APK, verifies its full SHA-256 and checks the APK ZIP structure.
3. **macOS/Linux/Windows with Python 3:** run `python3 assemble_test_apk.py` (Windows: `py -3 assemble_test_apk.py`). This verifies zlib, every piece, the entire APK SHA-256 and the APK ZIP CRC. Works offline from the extracted folder.
4. When the script reports success, copy `XuaneyNote-0.2.0-pr-b-6614745c341b-debug.apk` to the Android test device and install it. Never install a partial file. Do not delete an installed app containing the only copy of real notes.

## Verified binary identity

- Original APK SHA-256: `07e3c53bea21768f936431e514e91f4dd4eeed0fd8490df800ccc53ab0aa687c`.
- Original APK size: **11,187,305 bytes**.
- Reconstructed compressed stream SHA-256: `16af9e9385acc5aec21a1a38cbd351b8e67a85f07bc054f147c895bc56824ee1` (**10,666,489 bytes**).
- CI temporary debug signing-certificate SHA-256: `23cc82196fa887bab92a2a09db11b41eeb0628dd0603d45ef48d5062920bc484`, verified by original Android Build Tools `apksigner` (APK Signature Scheme v2).
- Package ID: `io.github.lihuihnu.xuaneynote.debug`. Version: `0.2.0-pr-b-debug` (versionCode 2). Android 8+ (minSdk 26), targetSdk 36.
- Exact Git blob SHA-1 and SHA-256/size for each part are in `manifest.json`. The full APK digest is verified *again* locally before the script produces the installable file.

## Safety and actual acceptance boundary

- **Different GitHub runs use ephemeral debug signing keys.** Even with versionCode 2, the PR-B APK may not install over a PR-A debug APK signed by a different certificate. Do not force it by uninstalling an app holding unbacked-up data. Use a separate disposable test device/profile.
- B3.3 supports **read-only previews of restored images**. Adding or removing images stays disabled until a separate human SAF/Photo Picker acceptance and CI. This APK is not a full release.
- ZIP v2 backups are **plaintext**; users choose their own SAF storage provider. The app does not automatically upload, but a manually selected provider may be a cloud service.
- Before changing or deleting anything, follow the [B4.2b synthetic-note real-phone SAF checklist](https://github.com/lihuihnu/XuaneyNote/blob/6614745c341bcd84019a40af2ab57d4ab7ee22b4/docs/B4_2B_SAF_ACCEPTANCE.md). Neither the previous emulator tests nor this APK transfer constitutes completed manual device acceptance.
- Ordinary CI had read-only repository access, no Actions Artifact, no distribution signing key. APK file bytes are on this dedicated download branch only as compressed split binary blobs, never in `main`.

**Important:** If the APK full-file SHA-256 differs from the pinned digest above, stop. Do not install.
