package io.github.lihuihnu.xuaneynote.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.lihuihnu.xuaneynote.model.ThemeMode
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore(name = "user_preferences")

class UserPreferences(context: Context) {
    private val store = context.applicationContext.preferences
    private val themeKey = stringPreferencesKey("theme")
    private val selectionKey = stringPreferencesKey("selected_note")
    val theme = store.data.map { data ->
        ThemeMode.entries.firstOrNull { it.name == data[themeKey] } ?: ThemeMode.SYSTEM
    }
    val selectedNote = store.data.map { it[selectionKey] }
    suspend fun setTheme(theme: ThemeMode) { store.edit { it[themeKey] = theme.name } }
    suspend fun select(id: String?) {
        store.edit { if (id == null) it.remove(selectionKey) else it[selectionKey] = id }
    }
}
