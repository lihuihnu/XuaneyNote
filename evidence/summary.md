# PR-B B0 · CI 38999957010 · 可读证据归档

- 官方 GitHub Actions [Run 38999957010](https://github.com/lihuihnu/XuaneyNote/actions/runs/38999957010) completed/success，job 117300594503、attempt 1。
- PR source SHA `426b44a9e0ee75549f2c1b58e3f968855f76bc36`，tested merge SHA `cbfd9eff24e6ff01b5205adba6866896eb51fa75`；官方 Ubuntu 24.04 Runner，image `20261012.334.1`。
- Model JUnit 9/9；Room/backup/migration instrumentation 7/7；Phone Compose 2/2；Tablet Compose 2/2。上述 XML failure/error 均为 0。设备是 API35 模拟器，不是独立平板真机。
- Room v1 Schema helper→v2 校验、生产 NoteDatabase.open 自动迁移、旧版 v1 JSON 导入/幂等/损坏拒绝均在数据库设备报告中成功。
- KSP 自动生成的 `NoteDatabase/2.json` SHA-256：`9ba603f08bc9e08eb5eee97cb6600c9aaa2fd90540cf16722184d5a9389ed445`，identityHash `3d28b225caeebc999ac8666827d96b7a`。
- 本归档：25 个从原始 Runner 可读记录提取并逐文件验证 SHA-256 的 UTF-8 文件；读本文本记录校验 `3eb7ed34ab701696dcc4f9daf2d5c2970fecfa3232f53affd89c68ab3e0cc7fb`。
- 严格注明：此为 **可读证据子集**，未包含所有原始日志、PNG/Gradle wrapper JAR 等二进制；可读层省略 10 项。完整原始传输仍在 CI 原始 job 日志中（观测到 158 块），本归档不宣称对其完整重建。
- 原始 CI 成功不等于回填 v2 Schema 后新 HEAD 已复验。关键源码回填须独立验收。
