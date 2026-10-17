# 官方 Runner 与仓库证据交付

## 唯一普通入口

`.github/workflows/ci.yml` 仅由 PR 变更或显式 workflow_dispatch 启动，不叠加 push、定时或评论入口。运行 `ubuntu-24.04` 标准官方 Runner；普通构建只有 contents:read，没有签名密钥、Artifact 或持久 Actions 缓存。checkout 不保留 Git 凭据；Action 使用经 GitHub 读取核实的完整 SHA。

文档修改在工作流内部分类，保留汇总检查，只做源文件/治理/协议轻量门禁。相关源码变更才下载 SDK、构建一次并启动一个 API 35 模拟器，顺序测试手机与平板窗口。新提交取消同 PR 旧验证，45 分钟明确上限。用户告知长任务结束后才继续读结果；不在聊天中轮询等待。

PR 用合并引用测试时记录 source_sha（PR HEAD）和 tested_sha（实际 checkout 的 merge SHA），两者不混用。只允许最终 PR HEAD 对应结果进入合并判断；旧结果保留历史身份但不充当新版本通过。

## 两阶段、只读构建的证据交付

第一阶段由 `tools/evidence.py emit` 把小型测试 XML、受限日志、schema、合成截图与工具链/命令/退出码，封装为带校验的分块数据写入 GitHub job 日志，并当场解码复核。日志只是**传输暂存**，不是已完成仓库归档。没有给不可信 PR 写仓库权限，也不执行日志内容。

第二阶段由维护者读取对应 job，先从 GitHub 核实 run/attempt、source/tested SHA 与结论，再用已审阅版本的解码器校验：

```sh
python3 tools/evidence.py unpack job.log evidence-out \
  --source-sha <完整PR_HEAD_SHA> --tested-sha <实际测试SHA> \
  --run-id <真实run_id> --run-attempt <真实attempt>
```

缺块、重复块、哈希不匹配、来源身份不符、超预算、路径穿越、疑似凭据或试图覆盖已有目录都必须拒绝。解码包仍是测试产生的不可信数据；仅允许保存和展示，不执行其内容。不得只信 payload 自报的 PASS。

经核验后，通过维护者 GitHub 连接器提交 `manifest.json`、`summary.md`、`results.json`、`logs/`、`reports/`、`schemas/`、必要截图和 `checksums.sha256`，再从远端读取验证内容及树身份。构建通过和证据入库/读回是两个独立门禁。受控失败的 exit code 和 failure 结论必须保留，不改写为成功。

### 初始引用方式的明确取舍

初始化规划优先考虑独立 Git evidence 标签。本次已发现的连接器可创建 Git 对象、提交和分支，但未提供标签写入动作；插件检索没有取得额外已连接标签发布接口。因此首期使用 `evidence/pr-a/<run>-a<attempt>-<source前缀>` 的**只读证据分支**及固定 commit SHA 作为导航/身份，不增加活动开发 PR，不修改 main。

证据分支创建后不得更新或 force-push；仓库管理员技术上仍可更改 Git 引用，不虚称已设置平台强制不可变规则。以完整证据 commit SHA 和文件 SHA-256 核验。后续若接入受信任标签发布器，可对已有证据提交追加标签，不重算、不覆盖历史、不开放普通构建写权限。

本地补充检查也可同样归档，但 manifest 必须写明不是 Android Runner 结果、实际工具版本、检查范围、源码身份。成功和故意 exit 17 的失败传输演练单独列明，不冒充应用测试失败或完整 Android 验收。

## 预算、引导文件与清理

单份封装文件最多 512 KiB，普通日志只保留末尾至多 64 KiB，记录原始字节数、原始 SHA、truncated 标记以及截断后 SHA；不得称截断日志为完整原始日志。传输 JSON 解压上限 8 MiB；超额非日志文件记 omissions，而不是偷偷删掉后称完整。日常证据目标仍小于规划 10 MiB。

首轮构建真实生成 Room v1 schema 和官方 Gradle Wrapper；schema 身份不得手填。Wrapper JAR 必须匹配官方 8.13 校验值 `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`，才可由维护者放回源码。未返回或未审阅提交时，PR-A 不合并。

普通 PR 的 APK 只用于模拟器，不作为分发包；记录身份，不把大 APK 塞进日志。后续安装包由独立、受信任发布流程进入仓库交付引用，附 APK 哈希和签名身份；不能宣称当前已有可下载 APK。

成功或失败均先收集并校验必要证据传输包，再清理本 job 的 build、Gradle 工作目录、发行包临时目录和 AVD；官方临时 VM 最终销毁。初期维护者在 job 结束后完成仓库写入，所以在远端读回完成之前始终标记 `transported-not-yet-repository-verified`，不把“已清理临时产物”包装成“已交付”。强制取消导致包不完整时报告真实缺口，不补造通过结果。

不得删除历史证据、用户笔记、外部导出文件、正式签名密钥或未知缓存目录。仓库完整历史 250 MiB 是预警预算；从工作树删除大文件不等于清除历史。

## 首轮配置校验修复记录

源码 `761ce57eaa5e16969a2212f28a30ead7eae87b90` 的首条记录 `38938316977` 在 GitHub 端即时失败，jobs 列表为空，未开始 Android 构建；该记录不是一次运行过的模拟器测试。检查发现 job 级 env 使用了不被允许的 `runner.temp` 上下文。修复将临时目录的设置放入第一个执行步骤，通过 `$RUNNER_TEMP` 写入 `$GITHUB_ENV`，并增加源文件回归检查。

依据：GitHub 官方 Context availability 表，`jobs.<job_id>.env` 允许 github/needs/strategy/matrix/vars/secrets/inputs，不含 runner： https://docs.github.com/en/actions/reference/workflows-and-actions/contexts#context-availability 。旧本地证据保持原 SHA 不改写；该修复不改 Android/Kotlin 应用代码，修复后的实际 Runner 结果另按新 source/tested SHA 归档。

## Run #3：完整成功证据归档

- 本次官方运行 [38988757189](https://github.com/lihuihnu/XuaneyNote/actions/runs/38988757189) 为 `success`，源码 `06ae6fc4b49a2c8f7b5b854e94b7e6de54251e37`，实际 tested merge `ed076740e734641c1e0d3b6870b47714a461a208`。
- 从 job 日志 157 块封装完整恢复，封装 SHA-256 `e697c2cc1a6864896ecc61305aee1ef3cbcd2b32032419b4911a55d5e3676897`。36 个原始载荷逐一核对大小与 SHA-256，包含原始 XML、日志、截图、Room schema、Gradle Wrapper。
- 证据分支 `evidence/pr-a/38988757189-a1-06ae6fc4`，固定证据提交 `e8b18d257dff10a332ee769a60db08ef27bb63e9`、文件树 `0e2e43207e8777cb9d8049a8a177250f572fc2bb`，已从远端读取并核对树与摘要/schema；原始 `manifest.json` 的 job 时点 `transported-not-yet-repository-verified` 不被篡改。
- 生成的 Wrapper/schema 作为**完全相同的 Git blob** 回填到开发分支。回填后的 SHA 须重新 CI；真机与 SAF 文件选择器手工测试仍未通过验收，不得宣布 V1 已发布。

## 受信任的一次性测试 APK 手工交付

用户明确要求准备安装包后进行真实设备验收。PR #1 暂时通过标签 `trusted-test-apk-once` 激活一个**仅限同仓库本人 PR 的只读日志传输步骤**；仍为官方 `ubuntu-24.04`、`contents:read`、无任何 Actions Artifact、无签名私钥。维护者必须在 GitHub 连接器中核验完整 run/source/tested 身份及包 SHA、`apksigner` 证书信息，再存入专门的下载分支并进行远端读回。构建结束前不声称提供了 APK。交付后移除临时标签，使普通 CI 不重复输送十余 MB 二进制。细则见 `docs/TRUSTED_TEST_APK_HANDOFF.md`。
