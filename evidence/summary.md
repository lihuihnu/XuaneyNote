# XuaneyNote PR-B B3.4 — official CI readable evidence

- Source: `236c322852b914a748dbd9f5b2a7b64e558ec0bf`; tested merge: `22a29b630d85e12f477ae799b57922950215c851`
- GitHub Actions Run: https://github.com/lihuihnu/XuaneyNote/actions/runs/39043407435 (attempt 1, job 117427256582, **success**)
- Room / backup tests: **38/38**; model **10/10**; simulated phone UI **9/9**; simulated tablet UI **9/9**; failures/errors/skips all 0.
- Android build, lint, merged Manifest privacy checks, restart probes, trusted APK transfer and cleanup: successful.
- Debug Photo Picker entry and image-backed permanent-delete regression exercised; Release image editing remains closed.
- SAF/ZIP v2 acceptance was previously reported by the maintainer, but physical-device Photo Picker, low-storage, EXIF and signed-upgrade checks have **not** passed here.
- The verified original signed Debug APK `XuaneyNote-0.2.0-pr-b-236c322852b9-debug.apk` has SHA-256 `d6acc01cf8998a2edcc264ca3cb6ca914821527a5283b47a518d278a5fefb16b`; Debug signer `c72085e4e877783438930fceb829b6d2d103bf04c5724b05f1e9065ca8256d73`; compressed stream SHA-256 `45a97a5a5aaffc5c767068c79789d97a988bb2791b488fd1557307a4acf675e6`.
- Immutable split APK archive: https://github.com/lihuihnu/XuaneyNote/tree/5be48a612a3478f49c3d555e9ad0c0932b434dd0
- 17 individually checked readable files recorded in this evidence directory; this does NOT include the entire binary envelope.

**Do not overwrite or uninstall the only copy of important notes to install an ephemeral-signed debug build. PR #2 stays Draft.**
