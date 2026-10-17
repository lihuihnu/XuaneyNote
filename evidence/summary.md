# B0 最终 CI 验收（可读证据子集）

- [最终 Run 39001250626](https://github.com/lihuihnu/XuaneyNote/actions/runs/39001250626)：**completed/success**（attempt 1, job 117304451962）。
- PR head `9cd9d35fe8045a0c616156d34d0504c230c15ea7`；实际 tested SHA `54dfef585ca0a71578011ecd8760fce31ad359d1`，两者已按独立身份核对。
- 模型 9/9、Room 与 B0 v1→v2/旧版 JSON 7/7、手机 2/2、平板 2/2，零失败；全部命名门禁退出码均为 0。
- 真实 KSP 生成的 Room v2 `2.json` SHA-256 固定为 `9ba603f08bc9e08eb5eee97cb6600c9aaa2fd90540cf16722184d5a9389ed445`，identityHash `3d28b225caeebc999ac8666827d96b7a`，与最终 PR 中已回填的文件一致。v1 冻结不动。
- 当前归档有 25 个可读原始文件/报告和逐文件哈希校验；可读记录 digest `be7ef5eb0f3c4846a1002cf3aa56221bb478c1a2e5a525fd61d5a26709222a0b`；10 项未进入可读子集。原始 job 日志还保留完整传输包，观测为 158 块。**不能将本次可读子集误称全量二进制证据归档。**
- 测试为同一 API35 模拟器手机/宽屏窗口，**不冒充**实体平板或保留同一签名的 PR-A→PR-B 真机覆盖升级。
- 该记录只对最终 B0 SHA 有效；B1 修改后须跑新 SHA 的 CI。
