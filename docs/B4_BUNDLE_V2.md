# B4：完整本地备份 v2 · B4.1 已验收，B4.2a 事务恢复候选

**日期**：2026-10-17，分支 feat/pr-b-data-safety，唯一活动 [PR #2](https://github.com/lihuihnu/XuaneyNote/pull/2)。
**基线**：B3.2 官方 [Run 39012222967](https://github.com/lihuihnu/XuaneyNote/actions/runs/39012222967) completed/success，源码 ee14433061534b3d152770d4ddc3e9ab2f08c7b0，tested a4dfe66ea9480d5a5144d06f15a526cce3b81e09。关键 XML 与 Schema 已固定归档 [f4df55ce](https://github.com/lihuihnu/XuaneyNote/tree/f4df55cecc529f193f42efbe568526896ace49b4/evidence)。
**现状**：B4.1 已通过 [官方 Run 39018703459](https://github.com/lihuihnu/XuaneyNote/actions/runs/39018703459) 并归档；B4.2a 的安全 Room/私有图片导出与恢复后端及设备回归为待验证候选，**尚未开放 SAF 操作或图片 UI**。

## 明文 ZIP v2 格式

- 格式标识 **XuaneyNote.bundle-backup**，版本 2，推荐后缀 .xuaney.zip；这是明文 ZIP，SHA-256 用于完整性检测，不等于加密、数字签名或攻击者认证。
- 唯一允许的 ZIP 文件名：manifest.json、notes.json 和 attachments/ 后跟 64 位小写 SHA-256。禁止目录条目、重复名、未知文件、反斜杠、绝对路径和穿越；图片名只来自已校验的 SHA，不直接使用 ZIP 文件路径解包。
- manifest.json 记录 format/version、database_version=3、UTC 导出时间、主题、笔记/附件/关联数量及每条目的字节大小与 SHA-256。
- notes.json 保留完整文本笔记字段、原始 UUID、创建及更新时间、修订号、格式及 deleted_at（含回收站），另含图片元数据与笔记—附件排序/共享引用。图片原字节以 content-addressed 文件单独存储，同 SHA 只存一份。
- 全量校验附件外键、重复 ID、无主图片、非法格式、大小与 SHA-256，且对 PNG/JPEG/WebP 实际解码 MIME 和尺寸再次验证。

## 预算与攻击面

- 压缩文件上限 **256 MiB**；实际展开总量 **512 MiB**；每图 **20 MiB**，附件元数据 **512** 项，ZIP 总条目最多 **514**。
- 最多 **10,000** 条 NOTE；manifest.json 不超过 **1 MiB**，notes.json 不超过 **24 MiB**；文本 UTF-8 总量初始预算 **8 MiB**（待真实设备性能评估）。
- 导出逐图以 8KiB 缓冲区复制并重新校验字节数/SHA，来源在写出过程中变化则抛错，不向用户宣称备份成功。
- 导入先将原始 ZIP 限额写入私有缓存，使用 ZipFile 完整中央目录而非忽略尾部的流式解压器；校验白名单/重复项/大小后仅解压到新建沙箱，逐条 SHA-256/格式核对。失败只删除本次暂存，**不修改原有笔记、回收站、图片或主题**。
- 此处的 ZIP v2 只是明文结构化格式；B5 必须独立进行成熟的密码加密库安全评审，不能把 SHA-256 当作密码保护。

## 三小步交付顺序

**B4.1 当前**：ZIP 数据格式、流式写入和隔离检验。没有数据库 restore，也没有用户入口。必须保留旧 PR-A JSON v1 恢复。
**B4.2 下一步**：用 Room 事务和图片文件锁取得一致快照，提供关闭后回读校验的 SAF 导出；导入先预检/确认，再以文件就绪→数据库事务→可恢复清理协议提交，处理重复导入与冲突副本，并独立报告主题设置失败。
**B3.3 与 B4.3 后续**：完整图片+回收站备份/恢复通过系统文件选择器和设备验收后，才开放图片按钮、缩略图与 EXIF 隐私说明。正式发版与签名仍另行评审。

## B4.1 需要真实 CI 检验的项目

- [x] Unicode、换行、Markdown、回收站及跨笔记共享图片往返完整一致；关闭沙箱移除暂存。
- [x] 空备份合法、v1 JSON 兼容保持独立。
- [x] ZIP-slip、未知文件、未知版本、重复 ZIP 路径拒绝。
- [x] 末尾中央目录截断、修改的图像字节、完整 ZIP CRC 下篡改 notes.json 均拒绝；错误不修改原 Room 数据。
- [x] 伪造引用、重复 ID、越界相对路径、错 Hash、无主图片拒绝。
- [x] 正确 Hash 但非真实图片拒绝；高度压缩且 notes.json 展开超限的 ZIP bomb 拒绝。
- [x] B0/B1/B2/B3.1/B3.2 既有测试、手机/平板模拟窗口及隐私测试继续通过，Room v1/v2/v3 Schema 哈希完全不变。
- [x] 对本次源码执行官方 GitHub-hosted ubuntu-24.04 CI，归档 Run/source/tested SHA、XML 和其他证据后才进入 B4.2。

**禁止过度宣称**：B4.1 编码完成、甚至 CI 通过，也不代表用户已经能够在 App 里完整导出、恢复图片或安全升级，不允许提前合并整个 PR-B。

## B4.1 真实 CI 结果（2026-10-17）

- [Run 39018703459](https://github.com/lihuihnu/XuaneyNote/actions/runs/39018703459) completed/success，source `fec827a6146e7f74002ec7812a07ae7f9da63b42`、tested merge `1f1a732b217503f50bbf07e3dc4609e24bfb6eb0`，attempt 1。
- Kotlin 10/10、Room/备份 30/30、模拟手机/平板各 4/4，失败/错误均为零。历史 v1/v2/v3 KSP Schema SHA 保持不变。
- [固定可读证据](https://github.com/lihuihnu/XuaneyNote/tree/d2fd7a22fd30fd71eba6e8c550fe1e0d1d718972/evidence) 包含 19 份独立归档并远端读回的可读子集；不冒充全量二进制/长日志或实体手机覆盖安装。
- 这轮只证明 ZIP 容器能够正确编解码和拒绝部分恶意输入，**没有执行生产数据库的 v2 恢复**。

## B4.2a：一致快照 + 冲突保留的安全恢复引擎（候选待 CI）

- `BundleTransferService` 与 `PrivateAttachmentFiles.withLockedFiles` 共用应用内附件锁；导出时在 Room 事务中获取完整 `notes`、`attachments`、`note_attachments` 快照，在文件锁持有期间复核源原图 SHA-256 并流式写出 ZIP。DataStore 主题跨存储原子性不存在，调用者必须独立处理其设置状态。
- `inspect(input)` 只能将未可信 ZIP 解压到沙箱；尚不修改数据库。`restore(inspected)` 必须由调用方在用户明确确认后执行，且在恢复前再次验证隔离图像的字节与 MIME。
- 数据库存在**同 ID 同文本但不同回收站状态/图片关联**时不得覆盖原件；生成由源笔记内容+图片内容标识确定的稳定“恢复副本”，再次导入同样备份幂等跳过。正常/回收站笔记各自保留 `deletedAt`，并不偷偷撤销已有删除。
- 共享图片的多笔记引用不会复制 ZIP 条目。图片 UUID 冲突时映射到稳定新 UUID 与私有受控路径；先准备并校验新的原图文件，再在一个 Room 事务中执行新增 notes/attachment/links。提交前复核现有 Room 数据/链接未被并发修改；失败则数据库回滚并清理本次无关联文件。
- 如果在图片文件落盘后进程被强制终止，启动 `recover()` 根据 Room 实际引用清理孤儿，不会删除已有有效引用。对已损坏的现有图片将拒绝复用，而非默默覆盖；还未接入 SAF 系统输出/输入交互。
- 应用启动在 IO 线程清理 `xuaney-bundle-v2-*` 的私有隔离预检缓存，不碰其他 cache 和系统文件。导出流的打开/关闭后再读回、用户确认界面、主题失败单独报告、文件选择器取消/恢复和系统厂商兼容仍由**B4.2b** 接线完成。
- 本阶段依然不向用户开放图片选择/缩略图，也不发布真正可携带重要资料的测试版本。

### B4.2a 新增设备门禁（需当前 SHA 的官方 CI）

- [ ] `BundleTransferServiceTest.exportedZipRestoresImagesTrashAndSharedLinksIntoEmptyDatabase`：真实 B3.2 合成原图的导出→隔离预检→空库恢复→重复导入幂等，中文、共享图片与已回收笔记往返保留。
- [ ] `sameIdConflictCreatesStableCopyWithoutOverwritingExistingNote`：同 UUID 不同正文和图片时保留本机原件、稳定恢复副本与附件，重复导入不增加笔记。
- [ ] `corruptedAfterInspectionCannotModifyExistingDatabase`：预检后再篡改已暂存图像，恢复失败无数据库变更、无新关联和新文件残留。
- [ ] `noteDeletionStateConflictRemainsSeparateAndIdempotent`：同 UUID 删除状态不同不得偷偷覆盖，保留原件和删除状态副本。
- [ ] `interruptedInspectionCacheIsCleanedWithoutTouchingOtherFiles`：重启清理只扫描应用受控缓存名，不删除无关数据。
- [ ] `repeatedRestoreDetectsDamagedExistingImageInsteadOfClaimingSuccess`：重复导入时必须核对已关联图片的物理字节，不得因元数据匹配而把丢失文件伪称已恢复。
- [ ] 全部旧版 B0～B4.1、模拟手机/平板、进程重启、隐私门禁及 Room v1/v2/v3 Schema Hash 仍通过。
- [ ] 真实 source/tested SHA 的 Run、JUnit 和可读证据独立归档并远端读回；通过后独立开发 **B4.2b 系统 SAF 交互、导出回读、导入预览/确认、主题失败恢复**。

**B4.2a 不等于 B4 完成；B4.2b 以及 B3.3 未通过前绝不开放用户插图。**
