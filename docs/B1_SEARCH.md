# B1：中文与英文笔记搜索

**阶段**：PR-B 数据安全与资料管理，分支 `feat/pr-b-data-safety`，目标 `main`。
**当前状态**：源代码 `6e4d1c4d850cf4230e646e0b73bbae0f54921a0f` 已通过 [官方 Run 39002603730](https://github.com/lihuihnu/XuaneyNote/actions/runs/39002603730)（completed/success）。B1 的已验证范围是 API35 模拟器查询和 UI 功能，不是已达到真机 P95 性能门槛。

## 需求与查询合同

1. “记录”页增加单行搜索框与明确的清空操作；“今日”页展示不受关键词影响的最近笔记。新建笔记时先清除过滤条件。空输入或全空格按普通列表处理。
2. 搜索在本地 SQLite 执行，使用 Room 绑定的 `:keyword` 参数以及 `instr(lower(title), lower(:keyword))` 和正文同类表达式。中文、Emoji 按连续原文子串查询，英文 ASCII 大小写不敏感；`%`、`_`、引号和反斜杠一律视为字面字符，不扩展为 SQL 模式或动态 SQL。
3. 只读取列表必需的 ID、标题、前 100 字摘要、修改时间。结果按 `updatedAt DESC, id ASC` 稳定排序。`deletedAt IS NOT NULL` 的软删除记录不会进入默认列表或搜索结果（B2 才交付删除 UI）。
4. 关键词最多 100 UTF-16 单元；非空搜索取至多 201 条并展示最多 200 条，如果存在第 201 条必须提示用户细化查询，不能把前 200 条谎称全部搜索结果。无搜索时暂保留 PR-A 的完整列表语义。
5. 输入 250ms 防抖、`flatMapLatest` 取消旧查询；查询中展示“正在搜索”，避免异步旧结果错配新关键词。数据库工作保持在 Room 后台执行，不在编辑 UI 线程上扫描正文。
6. **不改 Room Schema**：保留并由仓库检查固定 `1.json`、`2.json` 的 SHA-256，不引入全文索引或新增数据库迁移。不要声称 SQLite `lower` 解决所有非英语大小写/中文分词。

## 最小 CI 测试

- `NoteSearchTest.literalChineseEnglishAndDeletedNotesAreHandledCorrectly`：合成数据覆盖标题/正文中文、英文 ASCII 大小写、字面 `%`/`_`/引号/反斜杠、SQL 注入样式文本、无结果、软删除过滤、修改后重新搜索及结果排序。
- `NoteSearchTest.syntheticDatasetsBoundResultsAndRecordSearchTiming`：使用 100/1000/10000 条合成笔记、7 次采样验证取回上限/顺序，在 API35 模拟器记录检索时间分布。**P95 ≤250ms 是 PR-B 初始待测预算，不用模拟器样本代替真实手机性能**。
- `NoteFlowTest.chineseBodySearchEmptyStateAndClear`：手机、平板布局各验证中文正文命中、空结果提示、清除关键词恢复全量列表。
- 持续通过 PR-A 自动保存/重启、B0 Room v1→v2、JSON v1 备份恢复与系统权限检查；Runner 只用标准 GitHub-hosted `ubuntu-24.04`。
- 若测试失败或运行时间/内存偏大，先小步修复，再继续 B1；**B1 合格并归档后才进入 B2 回收站**。

## 范围边界

不增加搜索远程服务、账号、广告或运行时 INTERNET 权限；不引入尚未验证的 FTS 分词、拼音搜索、OCR、标签、附件、回收站管理或正式发布签名。用户当前保存的笔记及 v1 备份不可被破坏性转换或静默清除。

## 验收记录（2026-10-17）

- 测试源 SHA `6e4d1c4d850cf4230e646e0b73bbae0f54921a0f`，tested merge SHA `a43065b8ab0502ee151c06b2695cce84f77cca38`。
- GitHub Actions [Run 39002603730](https://github.com/lihuihnu/XuaneyNote/actions/runs/39002603730) completed/success，Model 9/9、Room/迁移/搜索 9/9、手机 3/3、平板 3/3，全部 failures/errors=0。
- [已归档可读证据](https://github.com/lihuihnu/XuaneyNote/tree/ab98bd010f184fe14713ad72e13270fe5026d6e5/evidence)：27 文件，含测试 XML、KSP schemas 及独立核验清单。它是可读子集，不冒充完整二进制封装。
- 10,000 条合成数据和 7 次采样的测试流程执行成功，但本轮被保留的可读报告未携带各次检索时间；**250ms P95 是待真实设备实测的性能预算，而非本次通过结论**。
- 下一步 B2 回收站；修改源码后必须按新的 PR HEAD 再跑 CI，不能复用本轮结论。
