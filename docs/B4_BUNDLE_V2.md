# B4：结构化本地备份 v2 · B4.1 安全 ZIP 编解码

**日期**：2026-10-17，分支 feat/pr-b-data-safety，唯一活动 [PR #2](https://github.com/lihuihnu/XuaneyNote/pull/2)。
**基线**：B3.2 官方 [Run 39012222967](https://github.com/lihuihnu/XuaneyNote/actions/runs/39012222967) completed/success，源码 ee14433061534b3d152770d4ddc3e9ab2f08c7b0，tested a4dfe66ea9480d5a5144d06f15a526cce3b81e09。关键 XML 与 Schema 已固定归档 [f4df55ce](https://github.com/lihuihnu/XuaneyNote/tree/f4df55cecc529f193f42efbe568526896ace49b4/evidence)。
**现状**：B4.1 只有待 CI 的 ZIP 编解码候选；尚未交付生产 Room 恢复事务、SAF 文件选取、可见图片和可用的完整备份 UI。

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

- [ ] Unicode、换行、Markdown、回收站及跨笔记共享图片往返完整一致；关闭沙箱移除暂存。
- [ ] 空备份合法、v1 JSON 兼容保持独立。
- [ ] ZIP-slip、未知文件、未知版本、重复 ZIP 路径拒绝。
- [ ] 末尾中央目录截断、修改的图像字节、完整 ZIP CRC 下篡改 notes.json 均拒绝；错误不修改原 Room 数据。
- [ ] 伪造引用、重复 ID、越界相对路径、错 Hash、无主图片拒绝。
- [ ] 正确 Hash 但非真实图片拒绝；高度压缩且 notes.json 展开超限的 ZIP bomb 拒绝。
- [ ] B0/B1/B2/B3.1/B3.2 既有测试、手机/平板模拟窗口及隐私测试继续通过，Room v1/v2/v3 Schema 哈希完全不变。
- [ ] 对本次源码执行官方 GitHub-hosted ubuntu-24.04 CI，归档 Run/source/tested SHA、XML 和其他证据后才进入 B4.2。

**禁止过度宣称**：B4.1 编码完成、甚至 CI 通过，也不代表用户已经能够在 App 里完整导出、恢复图片或安全升级，不允许提前合并整个 PR-B。
