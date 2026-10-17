# B0：PR-A Room v1 → PR-B Room v2 安全迁移

**实施日期**：2026-10-17，仓库遵循 Asia/Tokyo 日期口径。
**代码基线**：PR-A #1 的合并提交 `b85e27d5c004f978850badc4865dea821c0b44d1`。
**状态**：B0 最终官方 CI 已成功，数据库迁移与旧备份兼容通过；正式发布签名下 PR-A→PR-B 实体设备覆盖升级仍属于独立验收，不冒称已执行。

## 冻结变更范围

- 保留 PR-A `notes` 表的全部八个旧列与 `id` 主键、`index_notes_updatedAt` 索引。
- 在 `notes` 末尾仅新增 `deletedAt INTEGER NULL`；所有 v1 记录迁移后该值必为 `NULL`，表示未删除。B0 **不开放**搜索、删除、回收站或图片附件 UI。
- `@Database(version=2)` + 显式注册 `DatabaseMigrations.MIGRATION_1_2`。唯一 SQL 为 `ALTER TABLE notes ADD COLUMN deletedAt INTEGER`；不重建/清空数据库，不启动破坏性迁移。
- 旧笔记的 ID、标题、正文、创建和修改时间、修订号、正文格式与格式版本逐字段保持；进入 Room v2 后可以正常编辑和关闭重开。保存时额外禁止向未来已删除笔记回写旧草稿。
- DataStore 主题和选中笔记没有 schema 变化；迁移时不得修改它们。
- v1 JSON 备份容器 `XuaneyNote.text-backup`、version 1 继续使用原 `BackupCodec` 导入，数据合同见 [BACKUP_V1.md](BACKUP_V1.md)。此次暂不切换 v2 ZIP/附件备份格式。
- 附件表和真正的软删除事务将在 B2/B3 引入，并按必要的下一版显式迁移；不会偷偷修改已冻结的 v2 schema。

## 可重复的验收

**真实 v1 Schema 测试**：
`DatabaseMigrationTest.prAV1SchemaMigratesWithoutChangingNotesOrPreferences`
用 `MigrationTestHelper` 按已提交且 SHA-256 固定的 `.../1.json` 创建原版 Room v1 数据库，注入两条包含中文、Emoji、换行、Markdown 和非零修订的合成记录；在 `runMigrationsAndValidate(2, true, MIGRATION_1_2)` 验证真实 v2 schema 后逐个断言八列原值、`deletedAt = NULL`、数据条数，随后通过应用真实 `NoteDatabase.open` 重开、编辑、拒绝旧修订覆盖、再重开，确认主题和选中项保留。

**生产路径自动升级测试**：
`DatabaseMigrationTest.productionOpenAutomaticallyUpgradesTheOriginalV1Database`
再次用冻结 `1.json` 创建含真实 v1 数据的旧库，**不调用测试工具的迁移函数**，直接经应用 `NoteDatabase.open` 打开并查询，要求 Room 自动升至版本 2、原文逐字相等；防止只测试了迁移 SQL，却忘记在生产数据库构建器注册迁移。

**旧备份测试**：
`DatabaseMigrationTest.frozenPrAV1BackupRestoresIntoV2Idempotently`
从 `src/androidTest/assets/pr_a_v1_export.xuaney.json` 读取 PR-A 格式的**固定合成样本**（未包含私人资料），其 UTF-8 `payload_bytes=617`、内层 payload SHA-256 为 `1c2573cd0c4e1cdec13eb7bcc01044139fa414f832c4169df83cfddb31090c0f`；导入新 v2 数据库，确认两条笔记逐字相等、重复导入幂等、改写/损坏文件会被拒绝且已保存数据库不变。

**原有测试回归**：`DataContractTest`、模型单测、手机/平板 Compose 自动保存与重启、Lint/最终 Manifest 隐私门禁必须继续通过。


## 首轮官方 CI 实际结果（回填前）

- [GitHub Actions Run 38999957010](https://github.com/lihuihnu/XuaneyNote/actions/runs/38999957010) 为 **completed/success**，job `117300594503`，attempt `1`。
- 源码 SHA `426b44a9e0ee75549f2c1b58e3f968855f76bc36`；tested merge SHA `cbfd9eff24e6ff01b5205adba6866896eb51fa75`，两者不可混用。
- 实际 XML：Room / 数据合约 / B0 三项迁移 7/7、模型 9/9、手机 Compose 2/2、平板 Compose 2/2；所有报告错误和失败数为 0。两类窗口是在同一 API35 模拟器顺序运行，并非实机覆盖。
- KSP 实际输出 `schemas/io.github.lihuihnu.xuaneynote.data.NoteDatabase/2.json`，字节数 2659，SHA-256 `9ba603f08bc9e08eb5eee97cb6600c9aaa2fd90540cf16722184d5a9389ed445`，Room identityHash `3d28b225caeebc999ac8666827d96b7a`。未手动填写身份哈希。
- 经过原始日志可读证据协议的完整 digest 与逐文件 SHA-256 校验，25 份关键文本/Schema/XML 已单独归档并读回至 [固定证据提交 `6dfea983`](https://github.com/lihuihnu/XuaneyNote/tree/6dfea983a8fbfbda3e3303e0bebc6cde6df7cf9d/evidence)，该归档**明确仅是可读子集**，不冒充全部二进制/长日志。
- 本提交将实际 v2 schema 字节回填到 `core/data/schemas/.../2.json`，并在 `tools/check_repo.py` 中固定 KSP 生成文件的真实 SHA-256。**必须对回填后的新 HEAD 再做官方 CI**；通过前不进入 B1。

## 最终 B0 源码验收

- 官方 [Run 39001250626](https://github.com/lihuihnu/XuaneyNote/actions/runs/39001250626) `completed/success`，job `117304451962`，attempt 1；PR source `9cd9d35fe8045a0c616156d34d0504c230c15ea7`；tested merge `54dfef585ca0a71578011ecd8760fce31ad359d1`。
- 模型 JUnit `9/9`、Room/备份/迁移 `7/7`、手机 Compose `2/2`、宽屏平板 Compose `2/2`，均为零失败/零错误；官方 Runner 的 SDK 构建、Lint、隐私与强制终止重启检查成功。
- KSP 实际输出 Room `2.json` SHA-256 `9ba603f08bc9e08eb5eee97cb6600c9aaa2fd90540cf16722184d5a9389ed445`，与已回填源码一致；PR-A 的 Room `1.json` 未变。
- [固定可读证据及测试 XML](https://github.com/lihuihnu/XuaneyNote/tree/d44387b424b71027eceabc002ba0d92386ad4222/evidence) 完成独立归档并从远端读回；明确为 25 个可读记录子集，而非全部日志与二进制。
- **B0 完成结论**：可以进入 B1；不能因此假定跨 Debug 签名覆盖安装、API26 实机或真实平板性能已经通过。

## CI / Schema 交付闸门

当前运行时 Room 版本固定为 2.8.4；新增同版本 `androidx.room:room-testing` 的 **androidTest-only** 依赖，生产 APK 不引入测试库。Schema 导出仍由项目 KSP 自动生成。

- [x] 官方 GitHub-hosted `ubuntu-24.04` 针对 B0 **最终源码 SHA** 运行通过；不得用 PR-A 或文档 CI 的结果替代。
- [x] 从实际 KSP 输出取得 `core/data/schemas/io.github.lihuihnu.xuaneynote.data.NoteDatabase/2.json`，核对并提交实际生成的 `identityHash`；**禁止手工推算或编造**。冻结的 v1 schema 不得改写。
- [x] 源代码 SHA、tested merge SHA、run/attempt、JUnit XML 和实际 generated schema 从 Runner 证据中核对；关键证据独立入库并远端读回。
- [x] 回填 v2 schema 后，若 HEAD 变化，应重新运行最终必要门禁。B0 全部通过后，才进入 B1 中文搜索。

**实体设备覆盖的诚实边界**：Room 迁移测试能够证明数据库兼容，却不能单独证明不同 CI 临时 Debug 签名可直接覆盖安装。真实 PR-A→PR-B APK 覆盖升级需要**同一包名及签名身份**，发布/固定签名流程后再做设备升级回归；禁止建议用户清除应用数据来规避签名不一致。
