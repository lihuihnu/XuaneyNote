package io.github.lihuihnu.xuaneynote.data

import android.content.Context

class LocalData(context: Context) {
    private val database = NoteDatabase.open(context)
    val notes = NoteStore(database)
    // B3.2 后端准备就绪；B4 完整备份前不开放用户插图按钮。
    val attachments = PrivateAttachmentFiles(context, database)
    // B4.2 后端处理一致快照/文件事务；SAF 页面将独立在下一轮接线和验收。
    val bundle = BundleTransferService(context, database, attachments)
    val preferences = UserPreferences(context)
}
