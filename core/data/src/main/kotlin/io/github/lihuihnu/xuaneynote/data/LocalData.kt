package io.github.lihuihnu.xuaneynote.data

import android.content.Context

class LocalData(context: Context) {
    private val database = NoteDatabase.open(context)
    val notes = NoteStore(database)
    // B3.2 后端准备就绪；B4 完整备份前不开放用户插图按钮。
    val attachments = PrivateAttachmentFiles(context, database)
    val preferences = UserPreferences(context)
}
