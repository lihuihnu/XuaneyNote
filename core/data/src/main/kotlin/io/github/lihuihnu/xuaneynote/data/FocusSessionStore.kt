package io.github.lihuihnu.xuaneynote.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.lihuihnu.xuaneynote.model.FocusPeriod
import io.github.lihuihnu.xuaneynote.model.FocusStatus
import io.github.lihuihnu.xuaneynote.model.FocusTimerSnapshot
import kotlinx.coroutines.flow.map

// 与原有主题、选中笔记的 DataStore 隔离；C0 不改变 Room Schema。
private val Context.focusSessionStore by preferencesDataStore(name = "focus_session")

/** 仅持久化当前计时阶段；不是已完成专注历史，备份/恢复不得自动继续旧设备的运行计时。 */
class FocusSessionStore(context: Context) {
    private val store = context.applicationContext.focusSessionStore
    private val periodKey = stringPreferencesKey("period")
    private val statusKey = stringPreferencesKey("status")
    private val remainingKey = longPreferencesKey("remaining_ms")
    private val anchorKey = longPreferencesKey("anchor_elapsed_ms")
    private val bootKey = intPreferencesKey("boot_count")

    val session = store.data.map { preferences ->
        val period = preferences[periodKey]?.let { saved ->
            FocusPeriod.entries.firstOrNull { it.name == saved }
                ?: error("无效的专注阶段：拒绝悄悄覆写旧状态")
        } ?: FocusPeriod.FOCUS
        val status = preferences[statusKey]?.let { saved ->
            FocusStatus.entries.firstOrNull { it.name == saved }
                ?: error("无效的计时状态：拒绝悄悄覆写旧状态")
        } ?: FocusStatus.READY
        val snapshot = FocusTimerSnapshot(
            period = period,
            status = status,
            remainingMillis = preferences[remainingKey] ?: period.durationMillis,
            anchorElapsedMillis = preferences[anchorKey] ?: 0L,
            bootCount = preferences[bootKey] ?: -1
        )
        check(snapshot.isValid()) { "计时文件不一致，拒绝覆盖；可先检查备份" }
        snapshot
    }

    suspend fun save(snapshot: FocusTimerSnapshot) {
        require(snapshot.isValid()) { "非法计时状态" }
        store.edit { preferences ->
            preferences[periodKey] = snapshot.period.name
            preferences[statusKey] = snapshot.status.name
            preferences[remainingKey] = snapshot.remainingMillis
            preferences[anchorKey] = snapshot.anchorElapsedMillis
            preferences[bootKey] = snapshot.bootCount
        }
    }
}
