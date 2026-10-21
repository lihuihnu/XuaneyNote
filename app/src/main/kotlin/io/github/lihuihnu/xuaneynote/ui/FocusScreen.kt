package io.github.lihuihnu.xuaneynote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lihuihnu.xuaneynote.FocusTimerViewModel
import io.github.lihuihnu.xuaneynote.R
import io.github.lihuihnu.xuaneynote.model.FocusPeriod
import io.github.lihuihnu.xuaneynote.model.FocusStatus
import java.util.Locale

/** 简洁专注页。C0 仅有本地可恢复计时，不请求通知或精确闹钟权限。 */
@Composable
fun FocusScreen(modifier: Modifier = Modifier, vm: FocusTimerViewModel = viewModel()) {
    val current by vm.displayed.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 20.dp).testTag("focus-screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(stringResource(R.string.focus_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.focus_subtitle), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val session = current
        if (session == null) {
            CircularProgressIndicator(modifier = Modifier.testTag("focus-loading"))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusPeriod.entries.forEach { period ->
                    val title = when (period) {
                        FocusPeriod.FOCUS -> R.string.focus_period_work
                        FocusPeriod.SHORT_BREAK -> R.string.focus_period_short
                        FocusPeriod.LONG_BREAK -> R.string.focus_period_long
                    }
                    val tag = when (period) {
                        FocusPeriod.FOCUS -> "focus-mode-work"
                        FocusPeriod.SHORT_BREAK -> "focus-mode-short"
                        FocusPeriod.LONG_BREAK -> "focus-mode-long"
                    }
                    FilterChip(selected = session.period == period,
                        onClick = { vm.select(period) },
                        enabled = !busy && session.status != FocusStatus.RUNNING &&
                            session.status != FocusStatus.PAUSED,
                        label = { Text(stringResource(title)) },
                        modifier = Modifier.testTag(tag))
                }
            }
            Box(Modifier.size(232.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { session.remainingMillis.toFloat() / session.period.durationMillis },
                    modifier = Modifier.size(216.dp),
                    strokeWidth = 9.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val seconds = (session.remainingMillis + 999L) / 1_000L
                    Text(String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60),
                        style = MaterialTheme.typography.displayMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.testTag("focus-clock"))
                    val label = when (session.status) {
                        FocusStatus.READY -> R.string.focus_ready
                        FocusStatus.RUNNING -> R.string.focus_running
                        FocusStatus.PAUSED -> R.string.focus_paused
                        FocusStatus.COMPLETED -> R.string.focus_completed
                        FocusStatus.INTERRUPTED -> R.string.focus_interrupted
                    }
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                when (session.status) {
                    FocusStatus.READY, FocusStatus.COMPLETED, FocusStatus.INTERRUPTED ->
                        Button(onClick = vm::start, enabled = !busy,
                            modifier = Modifier.testTag("focus-start")) {
                            Text(stringResource(if (session.status == FocusStatus.READY)
                                R.string.focus_start else R.string.focus_start_over))
                        }
                    FocusStatus.RUNNING ->
                        Button(onClick = vm::pause, enabled = !busy,
                            modifier = Modifier.testTag("focus-pause")) {
                            Text(stringResource(R.string.focus_pause))
                        }
                    FocusStatus.PAUSED ->
                        Button(onClick = vm::start, enabled = !busy,
                            modifier = Modifier.testTag("focus-resume")) {
                            Text(stringResource(R.string.focus_resume))
                        }
                }
                if (session.status == FocusStatus.RUNNING || session.status == FocusStatus.PAUSED) {
                    OutlinedButton(onClick = { confirmReset = true }, enabled = !busy,
                        modifier = Modifier.testTag("focus-reset")) {
                        Text(stringResource(R.string.focus_reset))
                    }
                }
            }
            Text(stringResource(R.string.focus_no_alarm_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("focus-error"))
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text(stringResource(R.string.focus_reset_title)) },
        text = { Text(stringResource(R.string.focus_reset_notice)) },
        confirmButton = {
            TextButton(onClick = { confirmReset = false; vm.reset() },
                modifier = Modifier.testTag("focus-reset-confirm")) {
                Text(stringResource(R.string.focus_reset))
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmReset = false }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
