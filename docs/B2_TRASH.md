# B2：本地回收站、恢复和永久删除

**开发分支**：`feat/pr-b-data-safety`（继续 PR #2），起点为 B1 源码 `6e4d1c4d850cf4230e646e0b73bbae0f54921a0f`。
**状态**：仅提交候选源码、设备测试与数据合同；**新提交的 Android CI 未运行成功之前不得宣布 B2 已完成或可发行**。

## 功能与 UI

- 编辑器通过有确认弹窗的“移入回收站”操作设置已存在的 `deletedAt`；普通“今日”/“记录”/搜索都不展示它。回收站列表可查看标题与摘要、恢复、永久删除；无自动清空定时器。
- “恢复”清空 `deletedAt`，恢复原 ID、标题、正文和创建/更新时间，并从回收站打开已恢复的笔记。UI 的永久删除必须二次确认，且**数据库只允许真正已经软删除的记录永久删除**。
- 使用已有 Room v2 真实 `deletedAt` 字段和事务/更新 SQL，不新增数据表/列，不改动 `1.json`/`2.json`、不引入破坏性迁移。
- 移入和恢复操作都通过数据库原子更新**提升 `revision`**，确保切换笔记、保存屏障以及旧自动保存延迟提交时，不会复活已删内容或覆盖恢复后的新状态。资料正文的内容不受修改；重复删除/恢复返回明确失败，不制造副本。
- 数据失败、空间不足或保存失败的旧草稿不能悄悄清除。操作前等待 `AutosaveQueue.flush()`，数据库提交成功才提示成功。
- **B2 只含文本笔记**，没有任何附件表和文件实体可删除；B3 上线图片时，必须同时补上共享附件引用检查、数据库与文件系统的可恢复清理流程，不能将纯文本删除门禁冒称已验证附件安全。

## v1 JSON 备份的过渡保护

PR-A 的 v1 JSON 没有 `deletedAt`；B4 完整备份上线之前，**回收站非空时立即拒绝新的 v1 导出**，而不是仅导出活动笔记或悄悄将删除状态归零。明确提示用户先恢复仍需备份的条目（或等 B4），无需也不建议永久删除后再备份。

旧版 JSON v1 导入始终可用。如果导入的笔记与回收站里的原 ID 相同，即使文字完全相同，也保留回收站原件并在正常笔记区生成稳定的恢复副本；再次导入同一份备份应跳过，不重复生成副本。完整备份状态留给 B4，旧 v1 不会被暗中扩展字段。

**边界**：永久删除只保证删除应用内数据库记录，不能保证闪存物理安全擦除，也不能删除用户此前导出到系统其他位置的副本。

## 必要测试

- `NoteTrashTest.softDeleteRestoreAndRevisionFencePreventResurrection`：删除→列表/搜索隐藏→回收站查询→重开仍存在→恢复；新旧修订号、正文、ID 和时间守恒；迟到的旧草稿保存拒绝。
- `NoteTrashTest.permanentDeleteOnlyAcceptsTrashedRows`：拒绝对正常笔记物理删除；软删除后只删目标 ID、不得影响另一个笔记；重复删除不误报成功。
- `NoteTrashTest.oldBackupCannotSilentlyOmitTrashAndImportPreservesDeletedRecord`：回收站非空则 v1 导出拒绝；旧版恢复创建稳定活动副本、已删原件保留、重复恢复幂等；移出回收站后正常 v1 备份继续可用。
- `ModelTest.trashedRecordImportMakesVisibleIdempotentCopy`：纯 Kotlin 冲突规划器保证被删原件不被静默跳过。
- `NoteFlowTest.moveToTrashRestoreAndPermanentDelete`：模拟手机与平板均从编辑器移入→回收站→恢复并活动页重开→再次移入→永久删除，确认框不省略。
- 保留 B0 v1→v2 升级、旧版 JSON 备份导入、B1 中文搜索、PR-A 自动保存/重启及隐私 Manifest 门禁；只用标准 GitHub-hosted `ubuntu-24.04`。

### 退出条件

- [ ] 候选 HEAD 的真实 Android CI、所有数据/界面门禁和 v1/v2 Schema SHA 检查成功。
- [ ] 实际测试 JUnit XML、source/tested SHA、Run ID、失败数与关键证据入库并远端读回；未覆盖真机项目明确记录。
- [ ] 审阅回收站、永久删除与 v1 备份兼容的安全边界，没有私密样例泄漏或无警告丢失。
- [ ] 通过后才进入 B3 图片附件；本阶段不合并整个 PR-B。
