# XuaneyNote PR-A verified testing APK bytes

**Not yet a one-click APK file:** This branch stores the *complete APK bytes in 11 ordered files* due to a GitHub connector upload size limit. They must be concatenated and verified before installation. The assembled filename is `XuaneyNote-0.1.0-pr-a-19532a929475-debug.apk`. The final SHA-256 MUST equal `9def70c30bed6953a02151ef217aa6935f9948094298c78fc186bd823038f170`.

CI: https://github.com/lihuihnu/XuaneyNote/actions/runs/38995733428 — completed/success. Source: `19532a929475e9ce5e9f7fb2589654fb67f101d1`. Tested PR merge: `3f049fd689016b0766f25018db541b2158b31da2`. Certificate SHA-256: `c8e2c35d3f7dc5f8867485baca46eb2a9f0efff6671b72f0b6357b7104c72ee4` (temporary Android Debug certificate, **NOT a production signing key**).

Use the versioned assembly helper included in this branch after it is added. Do not install a partial file or run the app with important data. Always verify the reconstructed APK SHA-256 and source before installation. The test APK must not be silently presented as a direct-download `.apk` until fully assembled. The app's offline data-handling, SAF export/import, Chinese IME, large font, TalkBack, phone/tablet behavior must still be inspected on physical devices.

No Actions Artifact is used; this separate test branch is not merged to `main` and no normal PR workflow obtained write permission.
