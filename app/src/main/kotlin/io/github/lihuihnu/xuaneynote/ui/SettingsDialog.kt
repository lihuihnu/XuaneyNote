package io.github.lihuihnu.xuaneynote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.lihuihnu.xuaneynote.R
import io.github.lihuihnu.xuaneynote.model.ThemeMode

@Composable
fun SettingsDialog(theme: ThemeMode, busy: Boolean, onTheme: (ThemeMode) -> Unit,
    onExport: () -> Unit, onImport: () -> Unit,
    onExportBundle: () -> Unit, onImportBundle: () -> Unit,
    onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
                listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark).forEach { (mode, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(selected = theme == mode, enabled = !busy, role = Role.RadioButton,
                            onClick = { onTheme(mode) }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = theme == mode, onClick = null)
                        Spacer(Modifier.width(12.dp)); Text(stringResource(label))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Button(onClick = onExportBundle, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("export-full-bundle")) {
                    Text(stringResource(R.string.export_bundle_backup))
                }
                OutlinedButton(onClick = onImportBundle, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("import-full-bundle")) {
                    Text(stringResource(R.string.import_bundle_backup))
                }
                Text(stringResource(R.string.bundle_plain_notice),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp))
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                OutlinedButton(onClick = onExport, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("export-legacy-backup")) {
                    Text(stringResource(R.string.export_backup))
                }
                OutlinedButton(onClick = onImport, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("import-legacy-backup")) {
                    Text(stringResource(R.string.import_backup))
                }
                Text(stringResource(R.string.backup_notice), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } })
}
