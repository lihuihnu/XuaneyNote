package io.github.lihuihnu.xuaneynote.model

/**
 * C0：纯 Kotlin 倒计时，不使用墙上时间推算运行时长。
 * 每次开始/继续只保存当前阶段剩余时长和 elapsedRealtime 的锚点。
 */
enum class FocusPeriod(val minutes: Int) {
    FOCUS(25), SHORT_BREAK(5), LONG_BREAK(15);
    val durationMillis: Long get() = minutes * 60_000L
}

enum class FocusStatus { READY, RUNNING, PAUSED, COMPLETED, INTERRUPTED }

data class FocusTimerSnapshot(
    val period: FocusPeriod = FocusPeriod.FOCUS,
    val status: FocusStatus = FocusStatus.READY,
    val remainingMillis: Long = period.durationMillis,
    val anchorElapsedMillis: Long = 0L,
    val bootCount: Int = -1
) {
    fun isValid(): Boolean {
        if (anchorElapsedMillis < 0 || bootCount < -1 ||
            remainingMillis !in 0L..period.durationMillis) return false
        return when (status) {
            FocusStatus.READY -> remainingMillis == period.durationMillis && anchorElapsedMillis == 0L
            FocusStatus.RUNNING -> remainingMillis > 0L && bootCount >= 0
            FocusStatus.PAUSED, FocusStatus.INTERRUPTED ->
                remainingMillis > 0L && anchorElapsedMillis == 0L
            FocusStatus.COMPLETED -> remainingMillis == 0L && anchorElapsedMillis == 0L
        }
    }
}

/** 所有操作可注入当前开机计数与设备单调时钟，因而可确定性测试。 */
object FocusTimer {
    fun advance(saved: FocusTimerSnapshot, elapsedNow: Long, bootNow: Int): FocusTimerSnapshot {
        if (saved.status != FocusStatus.RUNNING) return saved
        // 不可识别设备启动标识、单调时钟倒退或换次启动：只报告中断，绝不把关机时间计为专注。
        if (bootNow < 0 || saved.bootCount < 0 || bootNow != saved.bootCount ||
            elapsedNow < saved.anchorElapsedMillis) {
            return saved.copy(status = FocusStatus.INTERRUPTED, anchorElapsedMillis = 0L)
        }
        val remaining = (saved.remainingMillis - (elapsedNow - saved.anchorElapsedMillis)).coerceAtLeast(0L)
        return if (remaining == 0L) {
            saved.copy(status = FocusStatus.COMPLETED, remainingMillis = 0L, anchorElapsedMillis = 0L)
        } else {
            saved.copy(remainingMillis = remaining, anchorElapsedMillis = elapsedNow)
        }
    }

    fun start(saved: FocusTimerSnapshot, elapsedNow: Long, bootNow: Int): FocusTimerSnapshot {
        require(bootNow >= 0 && elapsedNow >= 0L) { "无法确认设备单调时钟或启动标识" }
        val current = advance(saved, elapsedNow, bootNow)
        return when (current.status) {
            FocusStatus.RUNNING -> current
            FocusStatus.PAUSED ->
                current.copy(status = FocusStatus.RUNNING, anchorElapsedMillis = elapsedNow, bootCount = bootNow)
            FocusStatus.READY, FocusStatus.COMPLETED, FocusStatus.INTERRUPTED ->
                FocusTimerSnapshot(current.period, FocusStatus.RUNNING,
                    current.period.durationMillis, elapsedNow, bootNow)
        }
    }

    fun pause(saved: FocusTimerSnapshot, elapsedNow: Long, bootNow: Int): FocusTimerSnapshot {
        val current = advance(saved, elapsedNow, bootNow)
        return if (current.status == FocusStatus.RUNNING) {
            current.copy(status = FocusStatus.PAUSED, anchorElapsedMillis = 0L)
        } else current
    }

    fun reset(period: FocusPeriod): FocusTimerSnapshot = FocusTimerSnapshot(period)

    fun select(saved: FocusTimerSnapshot, period: FocusPeriod): FocusTimerSnapshot =
        if (saved.status == FocusStatus.RUNNING || saved.status == FocusStatus.PAUSED) saved
        else reset(period)
}
