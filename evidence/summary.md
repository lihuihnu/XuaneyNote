# B3.3 图片只读预览与受控 UI · 官方 CI 可读证据

- [Run 39034356792](https://github.com/lihuihnu/XuaneyNote/actions/runs/39034356792) **completed/success**, attempt 1, job 117401083102。
- Source `b14c900ce2b330e57ff4e3f2b885085019bb2157`; tested merge `40df2e8e21a34cd2469178401b1d73d07fc393b9`，必须区分源提交和被测 merge ref。
- Kotlin 模型 **10/10**、Room/图片/ZIP **38/38**、手机 Compose **8/8**、平板 Compose **8/8**，全部 errors/failures=0；所有命名 CI gates exit_code=0。
- 新的专项：`verifiedThumbnailIsBoundedAndCorruptionFailsClosed`、`normalApplicationCannotOpenImagePickerBeforeManualSafAcceptance`、`readOnlyComponentShowsRecoveredImageButNoAddOrRemove`、`editableComponentHasAddAndExplicitRemoveConfirmation` 均出现在真实 XML 并成功。
- 历史 Room v1/v2/v3 KSP SHA 未改变。主应用 `ImageUiGate.USER_ENTRY_ENABLED=false`，本次并未向普通用户开放添加/移除图片。
- 来源是 GitHub-hosted Android API 35 模拟器的手机和宽屏窗口，不等于实体手机/平板、真实 SAF/Photo Picker 点击验收或同证书覆盖安装。
- 20 份可读 Schema、JUnit XML 和少量日志独立归档。原始 Runner 可读记录尾部 digest `df04bfc27d5c081889c274f5227541d99633c6c4993ccb37955b19669186b6da`，完整 evidence envelope 保留在 [原始 job 日志](https://github.com/lihuihnu/XuaneyNote/actions/runs/39034356792/job/117401083102)：180 chunks，摘要 `24783ef23f1d5a0a0f4d24306b6cddb11a08496c249cc0c0fa2698dbfaa31a46`。
- 本归档**仅是可读子集**，省略 16 项二进制/长日志。APK 为 Runner 临时 debug 签名且未发布，不应声称存在可直接下载 APK 或已完成真机升级。
