# B4.2b · ZIP SAF 导出/恢复 UI · 可读 CI 证据

- [Run 39032360669](https://github.com/lihuihnu/XuaneyNote/actions/runs/39032360669) completed/success，attempt 1、job 117395237072；source `399e2959ac565e6176e8f86efa854949a740f8e1`，实际 tested merge `0d9c651913e6e844297ee961c641a65ca578616f`。
- GitHub-hosted ubuntu-24.04，Kotlin 模型 10/10、Room/备份/文件系统提供者 37/37、手机模拟 5/5、平板窗口模拟 5/5，全部 0 errors/failures；构建、Lint、隐私检查均成功。
- 新增 `systemContentProviderSupportsClosedZipReadback`（MediaStore 合成 content URI，而非用户真实 SAF）、`fullZipEntryRequiresWarningAndKeepsLegacyBackup`（设置页提示/取消及旧 JSON v1 入口）。
- Room `1.json`/`2.json`/`3.json` 的 KSP SHA 与此前固定身份完全一致；未开放用户图片插入 UI。
- 可读证据归档 23 项，日志记录 digest `8ee28511c4a513e69dacd5f07a61b488b5d0ca43ddce19b13234b7e0d0f9f6dd`。原始 job 保有 180 个传输块，摘要 `cbcc5a06fda24f350d19db1562a0efe450b0903c7d965ea3b64812196c107b85`。此提交仅是**可读报告子集**（省略 13 项大日志/非文本），不冒称完整证据。
- **尚未实际验收**手机/平板真机的 SAF CreateDocument / OpenDocument、生产签名升级、第三方云盘 provider、用户图片选择和真机性能。必须继续保留这些边界，不用 CI 成功冒充真实设备安全发布。
