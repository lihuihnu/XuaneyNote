package io.github.lihuihnu.xuaneynote.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lihuihnu.xuaneynote.NotesViewModel
import io.github.lihuihnu.xuaneynote.R
import io.github.lihuihnu.xuaneynote.designsystem.XuaneyTheme
import io.github.lihuihnu.xuaneynote.model.NoteSummary
import io.github.lihuihnu.xuaneynote.model.SaveState
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteApp(vm: NotesViewModel = viewModel()) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val states by vm.saveStates.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val backup by vm.pendingImport.collectAsStateWithLifecycle()
    var settings by rememberSaveable { mutableStateOf(false) }
    var exportWarning by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(1) }
    val snackbar = remember { SnackbarHostState() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(vm::exportBackup)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(vm::inspectBackup)
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    BackHandler(enabled = note != null && !settings && backup == null && !exportWarning) { if (!busy) vm.closeNote() }

    XuaneyTheme(theme) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            val labels = listOf(R.string.today, R.string.records, R.string.focus_later, R.string.journal_later)
            val icons = listOf(Icons.Default.Home, Icons.AutoMirrored.Filled.List, Icons.Default.Info, Icons.Default.DateRange)
            Scaffold(
                topBar = {
                    TopAppBar(title = {
                        Column {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.tagline), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }, navigationIcon = {
                        if (!wide && note != null) IconButton(onClick = vm::closeNote, enabled = !busy) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }, actions = {
                        IconButton(onClick = { settings = true }, enabled = !busy) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.settings))
                        }
                    })
                },
                bottomBar = {
                    if (!wide && note == null) NavigationBar {
                        labels.forEachIndexed { index, label ->
                            NavigationBarItem(selected = page == index, enabled = index < 2 && !busy,
                                onClick = { page = index }, icon = { Icon(icons[index], null) },
                                label = { Text(stringResource(label), maxLines = 1) })
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("busy"))
                    if (states.values.any { it is SaveState.Failed }) {
                        TextButton(onClick = vm::retrySaves) {
                            Text(stringResource(R.string.global_save_failed), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Row(Modifier.weight(1f).testTag(if (wide) "two-pane" else "single-pane")) {
                        if (wide) NavigationRail {
                            labels.forEachIndexed { index, label ->
                                NavigationRailItem(selected = page == index, enabled = index < 2 && !busy,
                                    onClick = { page = index }, icon = { Icon(icons[index], null) },
                                    label = { Text(stringResource(label)) })
                            }
                        }
                        if (wide || note == null) {
                            NoteList(notes, note?.id, busy, page == 0, vm::createNote, vm::openNote,
                                if (wide) Modifier.width(320.dp) else Modifier.weight(1f))
                        }
                        if (wide) VerticalDivider()
                        val selected = note
                        if (selected != null) {
                            NoteEditor(selected, states[selected.id] ?: SaveState.Saved(selected.revision), busy,
                                vm::editTitle, vm::editBody, vm::retrySaves, Modifier.weight(1f))
                        } else if (wide) {
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.pick_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (settings) SettingsDialog(theme, busy, vm::setTheme,
            onExport = { settings = false; exportWarning = true },
            onImport = { settings = false; restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onDismiss = { settings = false })
        if (exportWarning) AlertDialog(onDismissRequest = { exportWarning = false },
            title = { Text(stringResource(R.string.confirm_export)) },
            text = { Text(stringResource(R.string.backup_notice)) },
            confirmButton = { TextButton(onClick = {
                exportWarning = false
                export.launch("XuaneyNote-${LocalDate.now()}.xuaney.json")
            }) { Text(stringResource(R.string.choose_file)) } },
            dismissButton = { TextButton(onClick = { exportWarning = false }) { Text(stringResource(R.string.cancel)) } })
        backup?.let { validated ->
            AlertDialog(onDismissRequest = vm::cancelRestore,
                title = { Text(stringResource(R.string.restore_title)) },
                text = { Text(stringResource(R.string.restore_detail, validated.notes.size)) },
                confirmButton = { TextButton(onClick = vm::confirmRestore, enabled = !busy) { Text(stringResource(R.string.confirm_restore)) } },
                dismissButton = { TextButton(onClick = vm::cancelRestore, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
        }
    }
}

@Composable
private fun NoteList(notes: List<NoteSummary>, selected: String?, busy: Boolean, today: Boolean,
    onCreate: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxHeight().padding(horizontal = 16.dp)) {
        Text(stringResource(if (today) R.string.recent_records else R.string.records),
            style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
        Text(stringResource(R.string.notes_count, notes.size), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        FilledTonalButton(onClick = onCreate, enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("new-note")) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.new_note))
        }
        if (notes.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.empty_description), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f).testTag("note-list"), contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(notes, key = { it.id }) { item ->
                    Card(onClick = { onOpen(item.id) }, enabled = !busy, shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (selected == item.id)
                            MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth().testTag("note-${item.id}")) {
                        Column(Modifier.padding(16.dp)) {
                            Text(item.title.ifBlank { stringResource(R.string.untitled) }, maxLines = 2,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            if (item.preview.isNotBlank()) Text(item.preview, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
