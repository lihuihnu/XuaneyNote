package io.github.lihuihnu.xuaneynote

import android.app.Application
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.lihuihnu.xuaneynote.data.FocusSessionStore
import io.github.lihuihnu.xuaneynote.model.FocusPeriod
import io.github.lihuihnu.xuaneynote.model.FocusStatus
import io.github.lihuihnu.xuaneynote.model.FocusTimer
import io.github.lihuihnu.xuaneynote.model.FocusTimerSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** C0：记录的是本次开机下的单调时钟锚点，绝不使用系统墙钟减一秒。 */
class FocusTimerViewModel(application: Application) : AndroidViewModel(application) {
    private val store = FocusSessionStore(application)
    private val deviceBootCount = runCatching {
        Settings.Global.getInt(application.contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrDefault(-1)
    private val lock = Mutex()
    private val saved = MutableStateFlow<FocusTimerSnapshot?>(null)
    private val now = MutableStateFlow(SystemClock.elapsedRealtime())
    private val mutableBusy = MutableStateFlow(false)
    private val mutableError = MutableStateFlow<String?>(null)
    val busy = mutableBusy.asStateFlow()
    val error = mutableError.asStateFlow()
    val displayed = combine(saved, now) { snapshot, instant ->
        snapshot?.let { FocusTimer.advance(it, instant, deviceBootCount) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            try {
                val restored = withContext(Dispatchers.IO) { store.session.first() }
                val reconciled = FocusTimer.advance(restored, SystemClock.elapsedRealtime(), deviceBootCount)
                if (reconciled.status != restored.status) {
                    withContext(Dispatchers.IO) { store.save(reconciled) }
                }
                saved.value = reconciled
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value = "本地计时状态读取或保存失败，已暂停操作，避免覆盖已有数据。"
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                val elapsed = SystemClock.elapsedRealtime()
                now.value = elapsed
                val current = saved.value ?: continue
                val advanced = FocusTimer.advance(current, elapsed, deviceBootCount)
                if (current.status == FocusStatus.RUNNING &&
                    advanced.status != FocusStatus.RUNNING) {
                    lock.withLock {
                        if (saved.value === current) {
                            try {
                                withContext(Dispatchers.IO) { store.save(advanced) }
                                saved.value = advanced
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                mutableError.value = "计时结束状态未能保存；请检查本机可用空间。"
                            }
                        }
                    }
                }
            }
        }
    }

    private fun change(transition: (FocusTimerSnapshot, Long) -> FocusTimerSnapshot) {
        viewModelScope.launch {
            lock.withLock {
                val current = saved.value ?: return@withLock
                mutableBusy.value = true
                try {
                    val next = transition(current, SystemClock.elapsedRealtime())
                    if (next != current) {
                        withContext(Dispatchers.IO) { store.save(next) }
                        saved.value = next
                    }
                    now.value = SystemClock.elapsedRealtime()
                    mutableError.value = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: IllegalArgumentException) {
                    mutableError.value = "无法获取系统启动标识，本设备暂不支持可靠的重启计时。"
                } catch (_: Exception) {
                    mutableError.value = "计时状态保存失败，操作未生效；请检查本机可用空间。"
                } finally {
                    mutableBusy.value = false
                }
            }
        }
    }

    fun start() = change { current, elapsed -> FocusTimer.start(current, elapsed, deviceBootCount) }
    fun pause() = change { current, elapsed -> FocusTimer.pause(current, elapsed, deviceBootCount) }
    fun reset() = change { current, _ -> FocusTimer.reset(current.period) }
    fun select(period: FocusPeriod) = change { current, _ -> FocusTimer.select(current, period) }
}
