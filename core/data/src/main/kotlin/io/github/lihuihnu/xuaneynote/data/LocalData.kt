package io.github.lihuihnu.xuaneynote.data

import android.content.Context

class LocalData(context: Context) {
    private val database = NoteDatabase.open(context)
    val notes = NoteStore(database)
    val preferences = UserPreferences(context)
}
