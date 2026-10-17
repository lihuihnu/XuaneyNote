# XuaneyNote PR-A — official CI Run 38993768936 / attempt 1

- **Verified final verdict:** `completed / success` on the official GitHub-hosted `ubuntu-24.04` Runner, job `117281614970`.
- Source HEAD: `c5a5230d0a44a063f8dbfed621833f7c2b5e31ad`; checked-out PR merge SHA: `6add833a89e9c58a62b64a832e611fcf4c8f85d7`. These are not interchangeable.
- Runner: `GitHub Actions 1000032148`; image: `20261012.334.1`.
- Gradle Wrapper 8.13 from the repository; model tests **9/9**, Room/backup contract **4/4**, phone Compose **2/2**, tablet Compose **2/2**, zero failures.
- Both phone and tablet scenarios separately passed `seed → adb force-stop → verify` with `OK (1 test)` in each phase. Phone/tablet are **two screen configurations of one Android 15 API35 emulator**, not two physical devices.
- Lint, debug APK compilation, merged Manifest network-permission checks, and CI cleanup passed.
- Evidence envelope: 157 ordered blocks, zlib-uncompressed 478512 bytes, SHA-256 `cde8c69dadeb32f3ba6a47ce85a1eb69088a86e8fabd38ae77ef0b965d37b8e5`; all 36 original files (349461 total bytes) passed per-file SHA-256 and exact length checks; no omitted/truncated files.
- Room schema `1.json` matches the checked-in frozen database v1 schema, SHA-256 `6c8b9e2ea45c170c1b81df901e748377fa109e7a0a74f96c1b17e2e144ae5e99`; Wrapper JAR SHA-256 `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`.
- Temporary debug APK metadata and SHA-256 are kept in `manifest.json`; **the APK itself is not published**. Ordinary CI artifacts and caches were cleared.
- **Still pending:** complete SAF file chooser export/restore roundtrip on real device; Chinese IME, TalkBack, large font, physical phone/tablet UX and API26 compatibility/performance. PR-A remains Draft until those acceptance gates.
- Original manifest field `archive_status=transported-not-yet-repository-verified` reflects the job-time state. Repository read-back is an independent post-job operation and is not retroactively forged.
