# B3.3 图片只读预览与图片编辑解除闸门合同

**工作分支**：`feat/pr-b-data-safety`；唯一活动 Draft [PR #2](https://github.com/lihuihnu/XuaneyNote/pull/2)。当前代码为 B3.3 候选，尚未取得自身源码 SHA 的官方 CI。

## 本轮可以开发和验证

- B4.2b 已通过模拟器 CI 的 ZIP v2 导入能够恢复 JPEG/PNG/WebP 私有图片；当笔记实际含有图片，编辑页可以展示**只读缩略图和放大的本地预览**。没有图片的笔记不显示额外面板。
- Room `observeForNote` 跟踪当前笔记附件，切换笔记取消旧订阅；`PrivateAttachmentFiles.thumbnail` 在 IO 线程核对 SHA-256、受控私有路径、原图 MIME 和尺寸，然后限制解码尺度，不能把原图片/大 Bitmap 存入 SavedState。
- AndroidX `PickVisualMedia` 只选择图片；旧 Android 系统由 ActivityX 自动回退到 SAF。图片存储只使用一次性 `content:` URI，转储原始字节到 app 私有目录，不保留系统图库 URI，不能申请广泛存储权限。
- UI 已准备添加、放大查看及移除确认组件；**正常 App 的新增/移除入口由 `ImageUiGate.USER_ENTRY_ENABLED=false` 关闭**。仅在独立的合成 Compose 组件测试中模拟这些操作。
- 原图可能包含 EXIF 拍摄日期或位置信息；ZIP v2 是明文备份且包含原图元数据。界面应明确提示，不声称自动剥离 EXIF 或加密。

## 本轮待 GitHub CI 的自动化门禁

- [ ] `NoteImageStripTest.editableComponentHasAddAndExplicitRemoveConfirmation`：隔离组件的添加按钮、移除取消、二次确认及单篇关联 UI。
- [ ] `NoteImageStripTest.readOnlyComponentShowsRecoveredImageButNoAddOrRemove`：只读模式可展示已恢复图片、放大预览，且没有新增/移除按钮。
- [ ] `NoteFlowTest.normalApplicationCannotOpenImagePickerBeforeManualSafAcceptance`：常规应用进入笔记编辑时图片新增按钮完全不可见。
- [ ] `PrivateAttachmentFilesTest.verifiedThumbnailIsBoundedAndCorruptionFailsClosed`：损坏原图 SHA 后无法继续假装可预览，但不会删除已有数据库附件和笔记。
- [ ] B0–B4.2b 已通过的 Room、JSON v1、ZIP v2、手机/宽屏 Compose、进程重启、隐私及 Room v1/v2/v3 Schema SHA 保持成功；新 source/tested SHA 和证据归档远端读回。

## 只有满足以下条件才允许单独解闸

- [ ] 维护者本人完成 [B4.2b 手机 SAF 手工验收清单](B4_2B_SAF_ACCEPTANCE.md)。这项**目前尚无完成证据**。
- [ ] 在真实手机通过 Photo Picker 插入 JPEG/PNG/WebP，进程重启后展示仍存在，ZIP v2 导出、读回、恢复能找回所有图片与文字，错误/取消/低存储不会造成误报成功。
- [ ] 覆盖安装使用同一签名身份；跨临时 CI Debug 签名不得强行覆盖。
- [ ] 解除 `USER_ENTRY_ENABLED=false` 须有独立提交、最新 SHA 全门禁成功、明确的人工设备验收记录。不能把本阶段只读组件测试当作真实图片添加已上线。

**禁止提前宣称**：本轮不交付用户自由添加图片、不代表真机 SAF 或 B5 密码保护已经验收，PR-B 仍不具备正式合并/发布条件。
