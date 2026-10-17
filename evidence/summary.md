# B1 中文搜索 · CI 可读证据归档

- [官方 Run 39002603730](https://github.com/lihuihnu/XuaneyNote/actions/runs/39002603730)，attempt 1，job 117308505337，**completed/success**。
- Source `6e4d1c4d850cf4230e646e0b73bbae0f54921a0f`；tested merge `a43065b8ab0502ee151c06b2695cce84f77cca38`；官方 GitHub-hosted ubuntu-24.04，Runner image `20261012.334.1`。
- 模型 9/9，Room/旧备份/B0 迁移/B1 搜索 9/9，手机 3/3，平板 3/3，全部 failures=errors=0。测试界面来自同一 API35 模拟器，不是两台实机。
- 搜索测试明确覆盖中文、英文 ASCII 大小写、百分号/下划线/引号/反斜杠的字面匹配、软删除过滤、修改后更新、以及 100/1,000/10,000 条合成数据与结果上限。
- 10,000 条合成数据**功能测试**通过，测试代码尝试输出 P95，但本次可读 XML 和受限日志未提供可复核的独立逐次延迟统计，因此 **真实设备 P95≤250ms 未获证明**。
- 可读证据 23 文件，见 manifest 和 checksums；原始文本传输摘要 `b792d5d547b3880be8ac14e9595dcdd154555dbf517b2fff86ba3a6a520883a5`，完整不可直接下载的原始 evidence envelope 仍留在 [CI job 日志](https://github.com/lihuihnu/XuaneyNote/actions/runs/39002603730/job/117308505337)，观测 173 块（源摘要 `3979993641c79c7a0e923e01a8f2f72efcdb4313b6240795146c4987868eaad1`）。这是**可读子集**，未包含完整 binary/长日志，省略 12 项。
- B1 在 PR #2 保留 Draft；后续 B2 代码产生新的 SHA，必须重新跑官方 CI，不能继承本次结果。
