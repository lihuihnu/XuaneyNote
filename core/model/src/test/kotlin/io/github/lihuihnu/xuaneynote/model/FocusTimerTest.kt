package io.github.lihuihnu.xuaneynote.model

import org.junit.Assert.*
import org.junit.Test

/** 轻量测试：无 Android SDK / 真实等待，覆盖 C0 计时状态边界。 */
class FocusTimerTest {
    @Test fun startsAtPresetDuration() {
        val started = FocusTimer.start(FocusTimerSnapshot(), 1_000L, 8)
        assertEquals(FocusStatus.RUNNING, started.status)
        assertEquals(1_500_000L, started.remainingMillis)
        assertEquals(1_000L, started.anchorElapsedMillis)
    }

    @Test fun pauseAndResumeDoNotCountPausedTime() {
        val begun = FocusTimer.start(FocusTimerSnapshot(), 10_000L, 4)
        val paused = FocusTimer.pause(begun, 130_000L, 4)
        assertEquals(1_380_000L, paused.remainingMillis)
        val resumed = FocusTimer.start(paused, 930_000L, 4)
        assertEquals(1_380_000L, FocusTimer.advance(resumed, 930_000L, 4).remainingMillis)
        assertEquals(1_350_000L, FocusTimer.advance(resumed, 960_000L, 4).remainingMillis)
    }

    @Test fun restartInSameBootRecoversFromSavedAnchor() {
        val persisted = FocusTimer.start(FocusTimerSnapshot(FocusPeriod.SHORT_BREAK), 1_000L, 11)
        val openedAfterProcessDeath = FocusTimer.advance(persisted, 61_000L, 11)
        assertEquals(FocusStatus.RUNNING, openedAfterProcessDeath.status)
        assertEquals(240_000L, openedAfterProcessDeath.remainingMillis)
    }

    @Test fun expiryIsIdempotentAndClamped() {
        val begun = FocusTimer.start(FocusTimerSnapshot(FocusPeriod.SHORT_BREAK), 1_000L, 1)
        val completed = FocusTimer.advance(begun, 999_999L, 1)
        assertEquals(FocusStatus.COMPLETED, completed.status)
        assertEquals(0L, completed.remainingMillis)
        assertEquals(completed, FocusTimer.advance(completed, 2_000_000L, 1))
        assertEquals(completed, FocusTimer.pause(completed, 2_000_000L, 1))
    }

    @Test fun rebootOrElapsedRollbackInterruptsWithoutFalseCompletion() {
        val begun = FocusTimer.start(FocusTimerSnapshot(), 45_000L, 12)
        val rebooted = FocusTimer.advance(begun, 46_000L, 13)
        assertEquals(FocusStatus.INTERRUPTED, rebooted.status)
        assertEquals(begun.remainingMillis, rebooted.remainingMillis)
        assertEquals(FocusStatus.INTERRUPTED, FocusTimer.advance(begun, 8_000L, 12).status)
        assertEquals(FocusStatus.INTERRUPTED, FocusTimer.advance(begun, 100_000L, -1).status)
    }

    @Test fun timeZoneOrWallClockChangesDoNotAffectMonotonicCountdown() {
        val begun = FocusTimer.start(FocusTimerSnapshot(), 80_000L, 2)
        // 壁钟变化不作为引擎输入；只有 elapsedRealtime 的差量有效。
        assertEquals(1_440_000L, FocusTimer.advance(begun, 140_000L, 2).remainingMillis)
    }

    @Test fun periodsCannotChangeMidSessionWithoutReset() {
        val short = FocusTimer.select(FocusTimerSnapshot(), FocusPeriod.SHORT_BREAK)
        assertEquals(300_000L, short.remainingMillis)
        val running = FocusTimer.start(short, 1_000L, 2)
        assertEquals(running, FocusTimer.select(running, FocusPeriod.LONG_BREAK))
        val paused = FocusTimer.pause(running, 2_000L, 2)
        assertEquals(paused, FocusTimer.select(paused, FocusPeriod.LONG_BREAK))
        assertEquals(900_000L, FocusTimer.select(FocusTimer.reset(paused.period),
            FocusPeriod.LONG_BREAK).remainingMillis)
    }

    @Test fun pausedSessionCanResumeAfterDeviceRebootWithoutCountingShutdown() {
        val begun = FocusTimer.start(FocusTimerSnapshot(), 5_000L, 1)
        val paused = FocusTimer.pause(begun, 10_000L, 1)
        val resumed = FocusTimer.start(paused, 90_000L, 2)
        assertEquals(2, resumed.bootCount)
        assertEquals(paused.remainingMillis, FocusTimer.advance(resumed, 90_000L, 2).remainingMillis)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotStartWithoutReliableBootIdentity() {
        FocusTimer.start(FocusTimerSnapshot(), 5L, -1)
    }

    @Test fun impossiblePersistedStateIsRejected() {
        assertFalse(FocusTimerSnapshot(status = FocusStatus.RUNNING).isValid())
        assertFalse(FocusTimerSnapshot(remainingMillis = -2L).isValid())
        assertFalse(FocusTimerSnapshot(status = FocusStatus.COMPLETED, remainingMillis = 1L).isValid())
        assertTrue(FocusTimerSnapshot().isValid())
    }
}
