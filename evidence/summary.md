# B4.2a 事务恢复后端 · 官方 CI 可读证据

- [Run 39021083940](https://github.com/lihuihnu/XuaneyNote/actions/runs/39021083940) completed/success, attempt 1, job 117362859338。
- Source `b210aff4b7d7865383f6d882ffaacdc49f518d88`、tested merge `a36badd37da8b7de1a47400076d5deec14303b03`。官方 GitHub-hosted Ubuntu24.04。
- Model 10/10、Room B0–B4.2a 36/36、Phone Compose 4/4、Tablet Compose 4/4，零 failure/error。
- 专项覆盖：真正的私有图像 ZIP 导出→事务恢复、重复导入幂等、同 ID 冲突稳定副本、回收站状态、沙箱图片篡改回滚、已存在图丢失时拒绝假成功、只清理受控暂存。
- Room v1/v2/v3 Schema 三份 SHA 均与冻结的 KSP 字节相同；编译、Lint、隐私 Manifest 检查通过。
- 原始可读记录 digest `56102fbd93cf081b0a45404154dc260a809ac0765e5dbfe0fe327edcc95b8667`，含 24 项关键可读报告。完整 transport digest `4f5142dce59406c0ead4ada3c6ed972305b9ccde0b0bfd055c5afe80a7823e2c` / 180 分片，留存于 [原始 job](https://github.com/lihuihnu/XuaneyNote/actions/runs/39021083940/job/117362859338)。
- 本分支归档仅可读子集（遗漏 12 项），不包含完整原始二进制/长日志。**尚未完成用户 SAF 导出回读/确认、真实手机文件选择器和 B3.3 图片 UI**。
