# B3.1 Room v3 附件模型迁移 · 官方 CI 可读证据

- [Run 39007183912](https://github.com/lihuihnu/XuaneyNote/actions/runs/39007183912) 完成且成功，job 117321930946，attempt 1。
- source `ac36435e248bfe95766090bac570d09a04493799`，实际 tested merge `f7b39ba85efc8d60658f985656c18b5507dbacab`。
- Kotlin Model 10/10、Room/迁移/附件 15/15、模拟手机 4/4、模拟平板 4/4；错误和失败均为 0。所有名义 CI gates exit 0。
- Room v1/v2 历史 schema 未修改。真实 KSP v3 `3.json` SHA-256 `dc57dc1df55e8d0ae29dfebaec166c868a2076b8c869e394578638c7bc7602ed`，identityHash `84282a06c1efbf280105d5bea41212f8`；三张表为 notes/attachments/note_attachments；外键约束和索引由实际迁移测试验证。
- B3.1 不开放图片导入，尚未实现图片字节生命周期或包含图片的完整备份。API35 模拟器结果不可冒称实体手机和平板升级验证。
- 从 job 原始日志的 `XUANEY_TEXT_FILE` 取得 22 项可读 Schema/XML/文本，原始可读封装摘要 `928c6fd38d0da306ed2faf812303e8b5c30c091e134f9c3fae11bcc19e24aeef`。完整封装仍位于 [job 原始日志](https://github.com/lihuihnu/XuaneyNote/actions/runs/39007183912/job/117321930946)，原始摘要 `f81b5cde6ae4bbd67f864ec32b19aaa9837b7638ad36acdce5386c567a7f341d`、179 分块。
- **边界：这是可读子集，未完整归档二进制和长日志，可读层省略 14 项。** 后续回填真实 KSP 3.json 的提交必须重新 CI。
