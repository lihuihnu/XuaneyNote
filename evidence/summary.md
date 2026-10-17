# B2 回收站官方 CI 可读证据

- GitHub [Run 39005974577](https://github.com/lihuihnu/XuaneyNote/actions/runs/39005974577) **completed/success**；attempt 1，job 117318427230。
- source SHA `af36b2de2451bbd492c04ad3b8c05361319da962`；tested SHA `1127966fd72e023cdbbbdee6ccf44f1b9ccbccab`，严格区别 PR head 与 merge ref。
- Kotlin 模型 10/10，Room / B0 / B1 / B2 数据测试 12/12，模拟手机 Compose 4/4、模拟平板 Compose 4/4，全部失败和异常数为零。
- B2 覆盖：软删除/恢复、迟到草稿的版本保护、永久删除只针对回收站、v1 备份不可静默遗漏回收站、旧备份导入后稳定副本与幂等。
- 旧版 Room v1/v2 schema 保持。Runner `ubuntu-24.04`，API35 同一模拟器调整手机/平板窗口；没有实体设备覆盖安装证明。
- 归档包含 21 份可读 Schema/报告/日志。原始文本记录摘要 `e0b8b2618dde3bdcee784f55a0825bed5a7c4927b949cfd7186390d0f1596d5a`，原 CI 包原始封装摘要 `8eaa4351fc8662e4e6184e902954eba90b41e2d4bdbf62e4972bb7258744ea8d`（178 分片）仍在 job 日志。**本次仅归档可读子集，不冒充完整二进制或长日志；可读层省略 14 项。**
