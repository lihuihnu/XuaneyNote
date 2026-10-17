# B3：图片附件 · B3.1 数据模型/迁移候选

**日期**：2026-10-17（Asia/Tokyo）。
**分支**：`feat/pr-b-data-safety`，复用唯一活动 [PR #2](https://github.com/lihuihnu/XuaneyNote/pull/2)。
**基线**：B2 `af36b2de2451bbd492c04ad3b8c05361319da962`，官方 [Run 39005974577](https://github.com/lihuihnu/XuaneyNote/actions/runs/39005974577) completed/success。B2 可读证据固定提交 `f4fe13a423c43b61fcdfc212e1fcb80a656cf8d9`。
**状态**：**B3.1 首轮官方 Android CI 已成功；实际 KSP Schema v3 已校验回填，等待回填后的最终 SHA CI。** 仍不允许用户插入图片，也没有包含图片的完整备份，不能声明整个 B3 已完成。

## B3 总体验收约束

必须同时保证文件引用、图片导入和备份恢复合同，避免“UI 能添加图片但无法备份/恢复”的数据损失风险。继续保持完全本地、无运行时联网权限、免费免登录。

本阶段拆成三个可独立审阅的小步（均仍属于 PR #2）：

1. **B3.1：元数据和数据库迁移**。Room v2→v3，新增 `attachments`（图片 ID、受控相对路径、检测类型、原字节数、SHA-256、创建时间、可选尺寸）以及 `note_attachments`（稳定关联键、展示顺序）。生产注册 v1→v2→v3 链；只新建表/索引，不重建旧 `notes`、不改 `deletedAt`。此时**禁止开放图片选择 UI**。
2. **B3.2：可靠文件生命周期**。系统 SAF/Photo Picker 获取 JPEG/PNG/WebP；私有目录中使用唯一临时文件，流式大小/哈希验证、图片实际解码与尺寸限制、落盘和数据库关联之间协调成功/回滚；严禁保留依赖临时外部 URI、路径穿越或广泛存储权限。实际跨笔记共享文件引用的安全清理应有重试/重启后可继续的状态机制；不能仅靠删除 Room 行来断言磁盘文件清理成功。
3. **B3.3：可见 UI 与完整备份衔接**。图片的添加/预览/缩略图和触摸体验要与**包含所有附件与回收站状态的安全备份 v2**同步开放；否则保持入口隐藏且不让用户新增附件。在 B4 全量备份和恢复可用之前，不能把 B3 标记为可供真实用户长期保存图片的完整交付。

## v3 迁移合同

- v1 与 v2 的冻结历史 Schema 文件仍逐字节保持（原 SHA-256 由 `tools/check_repo.py` 固定）；新 `3.json` 必须**从此次 GitHub Runner 实际 KSP 产物取得**，校验 SHA-256 与 generated `identityHash` 后提交，禁止手填。
- 表 `attachments`：主键 `id`，唯一 `relativePath`；`sha256` 索引、`mimeType`、`byteSize`、`createdAt`、`width`、`height`。图片字节不会存在 Room BLOB/正文。
- 表 `note_attachments`：组合主键 `(noteId, attachmentId)`，外键 note→notes 级联、attachment→attachments 限制删除；attachmentId 索引。删除一个笔记不可破坏仍被其他笔记引用的附件。
- **过渡安全锁**：若存在任何图片元数据，旧 v1 JSON 导出（预检和事务内部）直接拒绝，不会生成缺图片的“成功备份”；若准备永久删除的笔记仍包含图片链接，先拒绝，直到 B3.2 的引用/文件清理流程完成。没有任何可进入生产环境的模拟附件数据。
- 旧 v1 JSON 导入保持有效：它不产生图片记录，也不会覆盖已有附件；Room v1→v2→v3 与 v2→v3 均不触碰旧正文/修订号/删除状态。
- 当前 `NoteStore.permanentlyDelete` 的禁止删除图片链接保护只是一道临时安全闸门，不是最终 B3 文件清理能力。


## B3.1 首轮官方 CI（Schema 回填前）

- [Run 39007183912](https://github.com/lihuihnu/XuaneyNote/actions/runs/39007183912) completed/success，job `117321930946`，attempt 1。
- source SHA `ac36435e248bfe95766090bac570d09a04493799`；tested merge SHA `f7b39ba85efc8d60658f985656c18b5507dbacab`。两种身份独立记录。
- Kotlin 模型 10/10；Room/数据与附件迁移 15/15；手机 Compose 4/4；平板 Compose 4/4，均 0 failures、0 errors。API35 的模拟手机/宽屏窗口不等于两台真机。
- Runner KSP 实际生成 `NoteDatabase/3.json`，7082 字节，SHA-256 `dc57dc1df55e8d0ae29dfebaec166c868a2076b8c869e394578638c7bc7602ed`，Room identityHash `84282a06c1efbf280105d5bea41212f8`。历史 `1.json`、`2.json` 未修改。
- 经过 Run 原始日志可读证据记录核验，22 份关键 Schema/XML/文本报告写入并读回 [固定证据提交 `09716d14`](https://github.com/lihuihnu/XuaneyNote/tree/09716d14cc4afd1233cd28d515bc15af6ebe7b58/evidence)。**明确只是可读子集，不包含所有长日志/二进制。**
- 现将该真实 KSP `3.json` 按实际原始 UTF-8 文本回填 PR，并在 `tools/check_repo.py` 固定生成内容 SHA-256、三表/外键边界；**新提交必须另行 CI，不能将这次通过代替回填后的复验**。

## B3.1 测试闸门

- [ ] `AttachmentSchemaTest.v2SchemaMigratesToV3WithoutChangingNotesOrTrash`：使用真实冻结 `2.json` 创建旧库，保留活动笔记和回收站笔记的全部文本、修订与删除时间；升级后检查两新表为空，真实 Room open 可用。
- [ ] `AttachmentSchemaTest.productionV1ToV3UpgradeKeepsOriginalNotebookData`：由生产 Room 直接执行完整 1→2→3 迁移，数据逐字节不变；没有破坏性重建。
- [ ] `AttachmentSchemaTest.linkedAttachmentCannotBeDeletedOrSilentlyExcludedFromV1Backup`：只用合成元数据，检查共享引用、旧 v1 备份拒绝、带引用永久删除保护。
- [ ] PR-A/B0/B1/B2 全部既有 Kotlin、Room/Compose 手机平板、进程重启、权限、备份测试持续通过。
- [ ] 从 KSP 实际生成的 v3 Schema 回填并校验、跟踪 CI 源码 SHA 与 tested SHA，独立归档证据并远端读回。
- [ ] 回填 `3.json` 后新 HEAD 再做最后一轮 CI；没有确认全部通过前不进入 B3.2。

**当前明确非目标**：不在 B3.1 交付 Photo Picker 交互、图片字节实际导入、缩略图、完整备份 ZIP、加密库或附件文件删除；也不修改正式签名、GitHub Runner、旧历史证据或 `main`。
