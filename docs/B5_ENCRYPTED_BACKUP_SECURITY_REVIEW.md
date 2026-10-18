# B5.0：可选 AES-256 ZIP 备份——依赖安全审查与实现合同

**审查日期**：2026-10-18（Asia/Tokyo）；**状态**：B5.0 安全审查已通过官方 [Run 39088201219](https://github.com/lihuihnu/XuaneyNote/actions/runs/39088201219)；B5.1 仅新增 test-only Zip4j 依赖、独立 JVM/7-Zip 探针及 API26 专项候选，**尚未运行本轮新 CI**。未新增生产加密代码或用户入口；不等于 B5 实现。唯一活动分支 `feat/pr-b-data-safety`，仍由 [PR #2](https://github.com/lihuihnu/XuaneyNote/pull/2) 管理，`main` 不直接修改。

## 0. 进入条件与现有基线

- 已验证的源码为 `236c322852b914a748dbd9f5b2a7b64e558ec0bf`；官方 [CI Run 39043407435](https://github.com/lihuihnu/XuaneyNote/actions/runs/39043407435) completed/success（tested merge `22a29b630d85e12f477ae799b57922950215c851`）：Room 38/38、模型 10/10、手机/平板模拟 9/9；[独立可读证据](https://github.com/lihuihnu/XuaneyNote/tree/8d667f21f0c403b992a4cd85ab535093fb0b882a/evidence)。
- 维护者先前报告 SAF/ZIP v2 手机验收通过，并于 **2026-10-18 明确报告 B3.4 手机端图片功能验收通过**。目前无设备型号、逐条测试或同签名覆盖升级报告，不能把未提供的细节记录为实测。
- 已有 JSON v1 导入及明文 ZIP v2 导入/导出不改变，Room v1/v2/v3 Schema 不变；图片编辑仍是 `ImageUiGate.USER_ENTRY_ENABLED = BuildConfig.DEBUG`，**Release 继续只读**。B5 不得以任何方式绕开 B4 的完整预检与先确认后提交。

## 1. 第三方密码 ZIP 库候选审查

| 检查点 | 当前判断 | 依据 / 剩余门禁 |
| --- | --- | --- |
| 软件与版本 | 候选 `net.lingala.zip4j:zip4j:2.11.6`，**明确固定版本而非动态范围** | [Maven Central](https://central.sonatype.com/artifact/net.lingala.zip4j/zip4j/2.11.6)、[上游 README](https://github.com/srikanth-lingala/zip4j) |
| 许可证 | Apache License 2.0，需在第三方声明中保留必要通知 | [上游许可证](https://github.com/srikanth-lingala/zip4j/blob/master/LICENSE) |
| 已知漏洞 | `CVE-2023-22899`：历史版本存在某些情况下不检查 AES MAC 的问题；已知修复版本为 **2.11.3**，所以必须拒绝 2.11.2 及以前版本 | [Red Hat CVE 跟踪](https://bugzilla.redhat.com/show_bug.cgi?id=CVE-2023-22899)。检索未发现 2.11.6 已公开的直接漏洞，不构成安全保证 |
| AES 实现 | 采用 Zip4j 的 `EncryptionMethod.AES` + `KEY_STRENGTH_256`，禁止 ZipCrypto/ZIP_STANDARD。需验证 AE-2 内容认证能拒绝被篡改和截断的数据 | [上游 AES 用法](https://github.com/srikanth-lingala/zip4j#creating-a-password-protected-zip-file--adding-files-to-an-existing-zip-with-password-protection) |
| Android 兼容 | 上游支持 Java 8/Android 使用场景，应用 `minSdk=26`；**Android API26 真机/模拟器尚未测** | [上游要求](https://github.com/srikanth-lingala/zip4j#requirements)；需要独立设备测试 |
| Unicode 密码 | Zip4j 接收 `char[]`，跨工具实现 UTF-8 编码处理；**中文、Emoji、组合字符及外部 7-Zip 实读尚未测** | [Zip4j 2.11.6 文档](https://javadoc.io/static/net.lingala.zip4j/zip4j/2.11.6/net/lingala/zip4j/ZipFile.html)；不得先断言完全互操作 |

依赖准入结论：**可作为下一轮隔离原型的候选，尚不可批准生产接线**。理由是独立解压互操作、API26 实测、异常认证路径和密码编码未完成；不得因官方 README 支持 AES 就伪称这些门禁通过。

## 2. 明确披露的密码安全边界

标准 WinZip AES-256 ZIP 虽使用 AES-CTR 加密和 HMAC-SHA1-80 内容认证，但密码 KDF 是 **PBKDF2-HMAC-SHA1、只有 1,000 次迭代**。攻击者取得备份后可以离线猜测弱密码；AES-256 密钥长度无法弥补弱密码。规范依据：[WinZip AES](https://www.winzip.com/en/support/aes-encryption/)。因此首版必须在导出 UI 显示高强度长密码建议和忘记密码无法找回的警告，不得宣传为抗暴力破解的现代高强度 KDF。

普通 AES ZIP 的文件名、条目数、部分时间和大小等**元数据默认不被加密/认证**。不可直接将含 `notes.json`、图片路径的原始 ZIP v2 各条目逐项加密，泄露资源布局。候选采用**双层容器**：仅加密一个固定名字的 `payload.xuaney.zip` 条目，其内容是完整、已经过 B4 校验的 ZIP v2。外层不包含用户笔记标题、图像哈希、主题或邮箱地址，记录的普通 ZIP 元数据尽量固定且不涉及笔记内容；**外层文件大小、外部文件名、可见时间等仍可能泄漏**。条目目录的真实性并非 AES MAC 所保护，必须拒绝多条目、明文条目、意外名称、弱加密方式和不符预期的 AES 强度，并在解密后再次执行 B4 完整验证。

## 3. 冻结的数据与失败合同

- 格式为标准 AES-256 ZIP，建议名称 `XuaneyNote-日期.xuaney.aes.zip`；仅一个加密条目 `payload.xuaney.zip`。保留老文件 `.xuaney.zip` 为 B4 明文 ZIP v2；**禁止在加密失败时悄悄回退成明文输出**，不自动替换用户已有目标。
- 导出流程：`AutosaveQueue.flush` → B4 一致快照与已有 ZIP v2 流式导出 → **应用私有有界临时文件** → Zip4j AES-256 封装 → SAF CreateDocument 写入并关闭 → 用同一 URI 读回 → 外层格式、密码认证、内层 B4 原始备份重新校验 → **确认相等后**反馈成功。任何失败明确报告，原 Room/图片不删除；残留 SAF 目标可能不完整，应告知用户不要使用。
- 导入流程：SAF OpenDocument → 有界拷贝到应用私有临时目录 → 根据实际内容识别 `plain-v2` 与 `aes-v2`；加密包只请求一次必要密码（允许重试但不输出密码）→ 检查中央目录**单条目**、AES-256 模式和资源限额 → 完整读取到 EOF 并验证 MAC → B4 `inspect` 校验内层全部图片/JSON/主题/回收站 → 显示预览 → 用户确认 → 现有 B4 原子恢复；密码错误、取消、篡改、文件超限和崩溃均不修改原库。
- 有界 I/O：沿用 B4 的压缩 ≤256 MiB、展开 ≤512 MiB、单图 ≤20 MiB 合同，并为**内层和外层分别**设置输入、解密明文及暂存磁盘限额；流式传输，禁止将整份 ZIP 载入 RAM。所有临时明文存储仅限应用私有目录，成功/失败主动清理，进程重启幂等扫尾；**文件系统上的临时明文不能宣称物理不可恢复擦除**。
- 密码仅用于本次操作，在应用 UI 层可能短暂以 `String` 存活，进入密码 ZIP 库前使用 `CharArray` 并在使用后及时覆盖；不写入 SavedState、DataStore、Room、日志、证据、截图、后台上传或崩溃上报。不要夸称 JVM/输入法/系统必然可零化所有副本。加密内容或密码长度不可作为日志。
- ZIP 认证覆盖内容并不等于中心目录、外层文件名完整性；加密导入必须对整个外层 ZIP 严格白名单校验，并对内层使用 B4 SHA-256/语义验证。SHA-256 仅证明本地完整性，不是密码认证的替代。
- 加密密码遗忘后离线应用无法找回，不做后门和预置通用恢复密码。不申请 `INTERNET`、`ACCESS_NETWORK_STATE` 或宽泛存储权限。

## 4. 最小、分阶段门禁（全部只在原 PR #2 进行）

1. **B5.0（已通过）**：冻结候选及残余风险、回填 B3.4 维护者通过声明；文档提交 `3ba6fa5b9a5eaf56ea0a7af0aa88133b46d49533`，官方 [Run 39088201219](https://github.com/lihuihnu/XuaneyNote/actions/runs/39088201219) completed/success。未增加运行时加密库。
2. **B5.1 依赖/格式独立验证（下一小步）**：隔离验证 Zip4j **2.11.6**；必要时先运行独立 JDK 工具测试和固定 SHA-256 库字节，之后在 API26 Android 合成设备验证 AES 256 读写、UTF-8 中文/Emoji 密码、空密码拒绝、错误密码、HMAC 被篡改、截断包，以及与独立 7-Zip **双向**读写。只做本算法/格式，先不接手机 SAF UI。发现问题先停止，改审成熟库；绝不降级为 ZipCrypto。
3. **B5.2 加密流与 B4 后端接线**：解码到私有有界沙箱，再强制走完整 B4 预检/事务/附件文件清理；重点验证在恢复确认前没有数据库写入，失败不改变既有笔记，重复导入幂等；不改 Room Schema。
4. **B5.3 明文/加密格式选择 + 手机/平板用户界面**：加入显示/确认密码、错误密码重试、取消、明文备份风险提示，SAF 实际导出→回读和导入→确认；只在完整校验后提示成功，验证异常 provider 和存储空间不足。
5. **最终 PR 门禁**：必要 Room/模型/手机/平板 + API26 + 真机文件提供者验收 + 独立证据（source/tested SHA、Run/attempt、真实 XML）；密码不出现在日志/证据中。全部通过再审阅将 PR #2 置为 Ready，**绝不因 B5.0 文档 CI 通过直接合并**。

## 5. 审核结论

- [x] 候选版本、许可证、历史 MAC 问题与可见弱 KDF/元数据风险有公开依据。
- [x] 选定避免用户内容泄漏的单条目外层方案（候选合同，不是已验证可行性）。
- [ ] Maven 发行 JAR SHA-256 固定及完整依赖审阅。
- [ ] Android API26 真正编译/运行。
- [ ] Unicode 密码与第三方 7-Zip 双向互操作。
- [ ] AES 内容认证、篡改、截断和格式降级拒绝。
- [ ] Android SAF 真实导出/恢复；取消/错误密码不会修改数据库。
- [ ] B5 正式使用前的隐私、性能、磁盘清理及人工验收。

**结论：B3.4 可以关闭当前人工验收等待，B5 仍处于审查/实现前置阶段。若 B5.1 任一安全门禁失败，不应把 AES-256 UI 提供给用户。**


## 6. B5.1 独立安全验证源码候选（2026-10-18，未通过新 CI）

本轮 **没有** 增加 `implementation(libs.zip4j)` 或更改已有 B4 生产备份接口。Zip4j 2.11.6 只在 `core:data` 的 `androidTestImplementation` 引入；由 `tools/b5/Zip4jInteropProbe.java` 这一份 Java 8 兼容源码同时为 Ubuntu JVM 和 Android 仪器测试服务。独立脚本 `tools/b5/run_interop.sh` 下载正式 Maven Central 2.11.6 JAR，先校验 Maven Central 同源 SHA-1 sidecar，再记录实际 JAR SHA-256 以待下一轮**独立确认并锁定**。同源 sidecar 不是独立可信 SHA-256 固定，不能因此直接生产准入。

**只运行合成资料的安全专项**：

- JVM：Zip4j 写 AES-256 ZIP → 独立 7-Zip 解包逐字节对照；独立 7-Zip 创建 AES-256 ZIP → Zip4j 严格单加密条目读取，对中文/Emoji 密码做双向验证。
- 负例：错误密码、仅篡改 AE-2 的 10 字节认证码、截断尾部、明文 ZIP、弱 ZipCrypto、额外 ZIP 条目，均必须实际抛错而不是靠 UI 说失败；读取完整文件到 EOF 后才接受 MAC 验证。
- Android：`B5Zip4jCompatibilityTest` 新增 3 个针对性测试，含正确 Unicode/错误密码、MAC/截断、弱格式降级；原 API35 手机设备套件执行一次，再独立启动 **API26** 模拟器，仅运行这 3 项（不重复其他大规模测试）。
- CI：现有唯一官方 `.github/workflows/ci.yml`，先单次 `b5-interop`，随后现有 Android 门禁 + `b5-api26`；工作流仍然只读，无 Actions Artifact、无持久缓存、不引入新 Runner 或自动循环触发。
- 禁止：将合成测试密码替换为用户真实密码，将测试 APK 当正式版，将 AES-256 的弱 KDF/外层元数据风险归零，把加密打包代码提前接进明文 ZIP v2 或 SAF UI。

**验收状态**（此次提交前，以下均未取得本源码新 Run）：

- [ ] 独立 7-Zip 双向 AES-256 + Unicode 字节一致。
- [ ] AE-2 MAC 内容认证、错误密码、截断、弱格式与多条目拒绝。
- [ ] API26 实际仪器测试与 API35 常规门禁完成。
- [ ] 官方 Run/source/tested SHA 与 JAR SHA-256 得到复核并归档。
- [ ] 进入 B5.2 前对实验结果复审，并将实际 JAR SHA-256 锁定为独立信任来源。

**已知风险**：标准 AES ZIP 的 PBKDF2 1000 次迭代和外层元数据公开仍不改变；部分第三方 ZIP 实现对非 ASCII 密码编码可能不一致。若互操作失败，应明确保留失败并审查，不做不透明密码转码或使用弱 ZipCrypto 回退。



## 7. B5.1 Run 39090047535 故障审计与修正合同（2026-10-18）

- [Run 39090047535](https://github.com/lihuihnu/XuaneyNote/actions/runs/39090047535)（source `4f8cde37d9838933a19e84863d3187d0d3eacb7e`，tested merge `490dcbeb70d9d6a78dd6501753de9a770a5798ff`）**completed/failure**。结构门禁、JDK 设置成功，Zip4j 测试已打印 `PASS B5.1 write-aes` 与 `PASS B5.1 verify`。紧接着独立 7-Zip 以包含中文/Emoji 的密码执行 `t` 时出现 `E_INVALIDARG`，退出码 2；Android SDK、API35 与 API26 均因前序失败未执行。
- 审核根因：ZIP AES 的**密码字节编码并没有跨工具统一的非 ASCII 语义**，7-Zip 和 Zip4j 的中文密码处理存在已知差异。[Zip4j 维护者说明](https://github.com/srikanth-lingala/zip4j/issues/328)、[7-Zip 作者对 ZIP 密码编码的说明](https://sourceforge.net/p/sevenzip/feature-requests/1322/)。**绝不能因为 Zip4j 自己能解密就把 7-Zip Unicode 互操作记为通过。**
- 补救门禁（不降低 AES-256/认证/弱加密拒绝）：`7-Zip` 使用 ASCII 高熵合成密码严格**双向**验证 AES-256 的通用格式；独立 Python `pyzipper 0.4.0` 使用 UTF-8 字节对中文/Emoji 密码与 Zip4j 进行严格双向、逐字节验收。两组都必须成功，否则本轮 CI 失败。7-Zip Unicode 另作为非阻塞的**兼容性观察**写入日志，遇到 `E_INVALIDARG` 不伪装为通过。
- Python 工具仅在 Runner 暂存目录安装，`pyzipper==0.4.0`、`pycryptodomex==3.23.0` 版本固定，不加到 Android 或发布依赖。Zip4j 测试 JAR 固定先前官方 Run 实见的 SHA-256 `5f1eb1e9d67cfee200a695e4079751b49e73a226b154193e13f7c602efbe72fb` 并在每次运行核对；**生产依赖的独立可信性审查仍未完成**。
- **不具备直接跨任意 ZIP 软件使用同一个中文密码的承诺**。如果后续确认标准 ZIP 无法解决这一兼容性缺口，应在 B5.2/B5.3 设计中明确区分“应用内支持 Unicode”与“外部兼容条件”，不得偷偷把 Unicode 密码转为另一组不向用户告知的字符，也不得改用不安全 ZipCrypto。外部 ZIP 互通的通用保证只针对强 ASCII 密码。若产品规格要求所有外部工具原样接受 Unicode，必须重新评审容器格式后再开放加密功能。
- 旧 `B5.1` 清单中的“**7-Zip 直接中文密码双向通过**”现认定是不可移植的测试假设，保留此失败证据，改以 **7-Zip ASCII + 独立 pyzipper Unicode** 双轨验证；新源码的 CI 结果必须单独记录，不能复用 Run 39090047535 当作通过。

本轮新代码仍是测试工具；未在生产 `implementation` 引入 Zip4j，明文 ZIP v2、Room Schema 和既有数据均不改变，PR 保持 Draft。


## 8. B5.1 成功验收与 B5.2 受控后端候选（2026-10-18）

B5.1 修订版 [Run 39090837334](https://github.com/lihuihnu/XuaneyNote/actions/runs/39090837334) **completed/success**，source `6144554b339323d021c682d095fab89ad3510645`，tested merge `cd9c7ad58295dd65d994b0092ea153ea68d9c8c1`，attempt 1：

- Zip4j + pyzipper 对中文/Emoji UTF-8 密码严格双向通过；Zip4j + 独立 7-Zip 对 ASCII 密码严格双向通过。7-Zip 在本 Runner 上额外观察到 Unicode 可解密，但不同环境不能以此保证全平台同一中文密码兼容。
- 完整 AES MAC 篡改、错误密码、截断、AES-128、ZipCrypto、明文与多条目拒绝通过；实际 API26 三项专项、原 API35 构建/Lint/模拟手机平板测试均通过。
- JAR SHA-256 `5f1eb1e9d67cfee200a695e4079751b49e73a226b154193e13f7c602efbe72fb` 在两轮官方 Maven 下载/CI 核对一致，独立可信来源仍应在发布审计中复核。风险：WinZip AES PBKDF2 1000 次、外层元数据泄漏、Unicode 第三方解压兼容不一，均**没有消失**。

本轮 **B5.2** 只把已审候选的 Zip4j 2.11.6 引入 `core:data`，并新增 `EncryptedBundleTransferService` 内部后端，不连接任何 Compose / SAF 界面；原明文 B4 代码与 Room v1/v2/v3 Schema 保持不变：

- 导出：原 B4 严格快照 → 私有临时 B4 ZIP → 单条目、固定文件名 `payload.xuaney.zip` 的 AES-256 外层 ZIP → 有界输出流。禁止 ZipCrypto/AES-128 降级，不把用户笔记标题或图片哈希写在 ZIP 条目名称上。B5.3 将负责 SAF 关闭后读回认证，当前不宣称已经具备用户可操作的完整加密导出。
- 导入：有界私有临时落盘 → 严格检查 ZIP 中央目录恰好一个固定名称、AES-256 强度、非分卷和容量 → 读取到 EOF 完成认证 → **必须再交给现有 B4 `inspect`** 做逐文件完整性、图片内容与数据库语义校验。只返回 `InspectedBundle`，**不会直接修改 Room**；单独调用 `restore()` 才会使用已经验证的 B4 原子恢复事务。
- 缓存：所有中间明文仅存在 app 私有 `cacheDir/xuaney-b5-*`，通过进程内 Mutex 序列化与启动时清理相同前缀的异常残留。错误/取消/损坏文件不会清空历史笔记，任何声明都不等于闪存物理不可恢复擦除。密码在后台使用临时 `CharArray` 副本并擦除，调用方仍负责自己传入的密码生命周期。
- 新增 **2 项** Android 合成数据专项：完整加密的笔记/回收站/共享图片往返与恢复确认前零数据库写入；错误密码、篡改 MAC、非法多条目和无效 ZIP 保持既有笔记不变。其余 B4 Room/图片/备份回归继续交由唯一官方 CI。
- **当前仅是提交前的候选源码与测试，尚未取得本轮 CI。** 即使成功，也只是后端原型，不代表真实 SAF、密码提示、加密文件读回或用户端功能完成；这些属于 B5.3。PR #2 仍 Draft，禁合并、禁发布。

B5.2 审查与合并边界：旧 B4 原子恢复和导入冲突处理绝不能复制成另一套；若加密认证失败或用户未确认，不能创建任何正常笔记/回收站/图片元数据。


## 9. B5.2 成功与 B5.3 密码备份界面候选（2026-10-18）

[Run 39093609921](https://github.com/lihuihnu/XuaneyNote/actions/runs/39093609921)（source `65f24f44e6f7b8ad3714d7d85f253b74e585df98`）已 **completed/success**：Room/图片/备份 43/43，模型 10/10、模拟手机/平板 UI 各 9/9，API26 AES 专项 3/3，原 B5.1 独立 7-Zip/pyzipper Unicode、篡改及降级门禁均成功。B5.2 后端可以供下一轮 **受控 UI 测试** 使用，不等于正式发布或真机 SAF 已通过。

B5.3 实施边界：

- 保留**三个独立格式**：JSON v1 文本、B4 明文 ZIP v2、B5 AES-256 密码保护 ZIP；旧格式可继续使用，不用密码方式覆盖或替换任何已有内容。
- 设置页面单独提供“导出密码保护备份”“导入密码保护备份”。导出密码必须输入两遍、长度 12–1024 字符，密码不放入 `rememberSaveable`、Room、DataStore、SavedState 或日志，使用 `PasswordVisualTransformation` 及 `SecureFlagPolicy.SecureOn`。在 SAF CreateDocument 期间只由 ViewModel 持有临时 `CharArray`；取消或退出即清除，不创建输出文件。
- **导出成功的必要条件**：保存队列全部 flush → Android SAF 写入 → 关闭文件 → 通过同一 URI 再次读取 → 完整 AES-256 认证 + 现有 B4 内层校验 → 比较导出原始 `BundleSnapshot` 与读回快照完全一致。否则如实报告外部目标文件可能不完整，本机资料不受影响，不假称完成。
- **导入成功的必要条件**：系统 SAF 选择 AES ZIP → 密码仅用于本次解密 → AES MAC 全量验证与 B4 预检成功 → 显示现有备份恢复预览 → 用户独立点击确认后**才调用原 B4 的 Room 事务恢复**。密码错误、取消、截断、篡改不会清空/覆盖现有资料；UI 允许重新选择文件、重新输入密码。
- 密码丢失不可找回；标准 WinZip AES 使用弱 PBKDF2 1000 次，建议使用**高熵长口令**，不能宣称抵御离线猜测。含中文/Emoji 的密码可在 Zip4j/pyzipper 下使用，但不是所有外部 7-Zip/系统解压工具都保证同样处理；外层大小、时间等元数据仍可见。
- 本轮新增受控密码对话框、合成 Compose 入口和密码校验测试、MediaStore 合成文件提供方**写入关闭—重开读回**测试；原数据库/图片/备份回归继续运行。真实 Android SAF 文件提供方及安装签名升级测试仍需维护者单独验收。
- **B5.3 当前为源码候选，新提交的官方 CI 未执行完成前不得宣称密码备份已可安全使用。** 保持 PR #2 Draft、`main` 不变；下一阶段要提供新的受信任测试 APK，继续按此前固定 Run、source/tested SHA、签名与分块 SHA-256 身份交付。验收清单：[B5.3 手机专项](B5_3_SAF_ACCEPTANCE.md)。
