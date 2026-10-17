package io.github.lihuihnu.xuaneynote.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import io.github.lihuihnu.xuaneynote.data.NoteStore
import io.github.lihuihnu.xuaneynote.R
import io.github.lihuihnu.xuaneynote.designsystem.XuaneyTheme
import io.github.lihuihnu.xuaneynote.model.NoteSummary
import io.github.lihuihnu.xuaneynote.model.SaveState
import io.github.lihuihnu.xuaneynote.model.ThemeMode
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteApp(vm: NotesViewModel = viewModel()) {
    val listing by vm.listing.collectAsStateWithLifecycle()
    val searchText by vm.searchText.collectAsStateWithLifecycle()
    val trashOpen by vm.trashOpen.collectAsStateWithLifecycle()
    val trashNotes by vm.trashNotes.collectAsStateWithLifecycle()
    val searchPending = searchText.trim() != listing.keyword
    val note by vm.note.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val states by vm.saveStates.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val backup by vm.pendingImport.collectAsStateWithLifecycle()
    val bundleReview by vm.pendingBundleReview.collectAsStateWithLifecycle()
    val notePictures by vm.imageAttachments.collectAsStateWithLifecycle()
    var settings by rememberSaveable { mutableStateOf(false) }
    var exportWarning by rememberSaveable { mutableStateOf(false) }
    var bundleExportWarning by rememberSaveable { mutableStateOf(false) }
    var moveToTrashRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var permanentDeleteRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var page by rememberSaveable { mutableIntStateOf(1) }
    // Photo Picker 返回前可能切换笔记：记录本次图片真正所属的笔记 ID。
    var pendingPictureNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(vm::exportBackup)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(vm::inspectBackup)
    }
    val exportBundle = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { it?.let(vm::exportBundle) }
    val importBundle = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { it?.let(vm::inspectBundle) }
    // AndroidX Photo Picker 返回一次性只读 content URI，仅 Debug 可发起。
    val pickPicture = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val intendedNoteId = pendingPictureNoteId
        pendingPictureNoteId = null
        if (uri != null && intendedNoteId != null) vm.addImage(intendedNoteId, uri)
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    LaunchedEffect(page) { if (page == 0) { vm.updateSearchText(""); vm.leaveTrash() } }
    BackHandler(enabled = note != null && !settings && backup == null && bundleReview == null && !exportWarning && !bundleExportWarning) {
        if (!busy) vm.closeNote()
    }
    BackHandler(enabled = trashOpen && note == null && !settings && backup == null && bundleReview == null && !exportWarning && !bundleExportWarning) {
        if (!busy) vm.leaveTrash()
    }

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
                        IconButton(onClick = { settings = true }, enabled = !busy,
                            modifier = Modifier.testTag("open-settings")) {
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
                            val panelModifier = if (wide) Modifier.width(320.dp) else Modifier.weight(1f)
                            if (trashOpen && page == 1) {
                                TrashList(trashNotes, busy, vm::leaveTrash, vm::restoreFromTrash,
                                    { permanentDeleteRequest = it }, panelModifier)
                            } else {
                                NoteList(
                                    notes = if (page == 1 && searchPending) emptyList() else listing.items,
                                    selected = note?.id, busy = busy, today = page == 0,
                                    searchText = searchText, searchPending = page == 1 && searchPending,
                                    onSearchChange = vm::updateSearchText,
                                    onClearSearch = { vm.updateSearchText("") },
                                    onCreate = { vm.updateSearchText(""); vm.createNote() },
                                    onOpen = vm::openNote, trashCount = trashNotes.size,
                                    onOpenTrash = vm::openTrash,
                                    modifier = panelModifier
                                )
                            }
                        }
                        if (wide) VerticalDivider()
                        val selected = note
                        if (selected != null && !trashOpen) {
                            NoteEditor(selected, states[selected.id] ?: SaveState.Saved(selected.revision), busy,
                                vm::editTitle, vm::editBody, vm::retrySaves,
                                { moveToTrashRequest = selected.id }, Modifier.weight(1f),
                                imageContent = if (notePictures.isNotEmpty() ||
                                    ImageUiGate.USER_ENTRY_ENABLED) {
                                    {
                                        NoteImageStrip(
                                            attachments = notePictures,
                                            busy = busy,
                                            allowEdit = ImageUiGate.USER_ENTRY_ENABLED,
                                            onAdd = {
                                                if (!busy) {
                                                    pendingPictureNoteId = selected.id
                                                    pickPicture.launch(PickVisualMediaRequest(
                                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                                    ))
                                                }
                                            },
                                            onRemove = vm::removeImage,
                                            loadImage = vm::imageThumbnail
                                        )
                                    }
                                } else null)
                        } else if (wide) {
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Text(stringResource(if (trashOpen) R.string.trash_detail_hint else R.string.pick_note),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (settings) SettingsDialog(theme, busy, vm::setTheme,
            onExport = { settings = false; vm.prepareLegacyBackup { exportWarning = true } },
            onImport = { settings = false; restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onExportBundle = { settings = false; vm.prepareBundleExport { bundleExportWarning = true } },
            onImportBundle = { settings = false; importBundle.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) },
            onDismiss = { settings = false })
        if (exportWarning) AlertDialog(onDismissRequest = { exportWarning = false },
            title = { Text(stringResource(R.string.confirm_export)) },
            text = { Text(stringResource(R.string.backup_notice)) },
            confirmButton = { TextButton(onClick = {
                exportWarning = false
                export.launch("XuaneyNote-${LocalDate.now()}.xuaney.json")
            }) { Text(stringResource(R.string.choose_file)) } },
            dismissButton = { TextButton(onClick = { exportWarning = false }) { Text(stringResource(R.string.cancel)) } })


        if (bundleExportWarning) AlertDialog(
            onDismissRequest = { bundleExportWarning = false },
            title = { Text(stringResource(R.string.confirm_bundle_export)) },
            text = { Text(stringResource(R.string.bundle_plain_notice)) },
            confirmButton = {
                TextButton(onClick = {
                    bundleExportWarning = false
                    exportBundle.launch("XuaneyNote-" + LocalDate.now() + ".xuaney.zip")
                }, modifier = Modifier.testTag("bundle-export-confirm")) {
                    Text(stringResource(R.string.choose_file))
                }
            },
            dismissButton = {
                TextButton(onClick = { bundleExportWarning = false },
                    modifier = Modifier.testTag("bundle-export-cancel")) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )

        bundleReview?.let { review ->
            AlertDialog(
                onDismissRequest = vm::cancelBundleRestore,
                title = { Text(stringResource(R.string.bundle_restore_title)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(stringResource(
                            R.string.bundle_restore_summary,
                            review.noteCount, review.trashedCount,
                            review.imageCount, review.linkCount
                        ))
                        val themeName = when (review.exportedTheme) {
                            ThemeMode.SYSTEM -> R.string.theme_system
                            ThemeMode.LIGHT -> R.string.theme_light
                            ThemeMode.DARK -> R.string.theme_dark
                        }
                        Text(stringResource(R.string.bundle_restore_theme, stringResource(themeName)),
                            modifier = Modifier.padding(top = 8.dp))
                        Text(stringResource(R.string.bundle_restore_conflict_notice),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp))
                        if (review.sampleTitles.isNotEmpty()) {
                            Text(stringResource(R.string.bundle_restore_preview_title),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 12.dp))
                            review.sampleTitles.forEach { Text("· " + it, maxLines = 1) }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = vm::confirmBundleRestore, enabled = !busy,
                        modifier = Modifier.testTag("confirm-bundle-restore")) {
                        Text(stringResource(R.string.confirm_restore))
                    }
                },
                dismissButton = {
                    TextButton(onClick = vm::cancelBundleRestore, enabled = !busy,
                        modifier = Modifier.testTag("cancel-bundle-restore")) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        moveToTrashRequest?.let { id ->
            AlertDialog(onDismissRequest = { moveToTrashRequest = null },
                title = { Text(stringResource(R.string.trash_confirm_title)) },
                text = { Text(stringResource(R.string.trash_confirm_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        moveToTrashRequest = null
                        vm.moveToTrash(id)
                    }, enabled = !busy, modifier = Modifier.testTag("confirm-trash-move")) {
                        Text(stringResource(R.string.move_to_trash))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { moveToTrashRequest = null }) { Text(stringResource(R.string.cancel)) }
                })
        }
        permanentDeleteRequest?.let { id ->
            AlertDialog(onDismissRequest = { permanentDeleteRequest = null },
                title = { Text(stringResource(R.string.trash_permanent_title)) },
                text = { Text(stringResource(R.string.trash_permanent_notice)) },
                confirmButton = {
                    TextButton(onClick = {
                        permanentDeleteRequest = null
                        vm.permanentlyDeleteTrashed(id)
                    }, enabled = !busy, modifier = Modifier.testTag("confirm-permanent-delete")) {
                        Text(stringResource(R.string.trash_permanent))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { permanentDeleteRequest = null }) { Text(stringResource(R.string.cancel)) }
                })
        }
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
private fun NoteList(
    notes: List<NoteSummary>, selected: String?, busy: Boolean, today: Boolean,
    searchText: String, searchPending: Boolean,
    onSearchChange: (String) -> Unit, onClearSearch: () -> Unit,
    onCreate: () -> Unit, onOpen: (String) -> Unit,
    trashCount: Int, onOpenTrash: () -> Unit, modifier: Modifier
) {
    val searching = !today && searchText.isNotBlank()
    val visible = if (searching) notes.take(NoteStore.SEARCH_VISIBLE_LIMIT) else notes
    val hasMore = searching && notes.size > NoteStore.SEARCH_VISIBLE_LIMIT
    Column(modifier.fillMaxHeight().padding(horizontal = 16.dp)) {
        Text(stringResource(if (today) R.string.recent_records else R.string.records),
            style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
        if (!today) OutlinedTextField(
            value = searchText,
            onValueChange = onSearchChange,
            label = { Text(stringResource(R.string.search_notes)) },
            singleLine = true,
            trailingIcon = {
                if (searchText.isNotEmpty()) TextButton(
                    onClick = onClearSearch,
                    modifier = Modifier.testTag("clear-note-search")
                ) { Text(stringResource(R.string.search_clear)) }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("note-search")
        )
        if (!today) TextButton(onClick = onOpenTrash, enabled = !busy,
            modifier = Modifier.testTag("open-trash")) {
            Text(stringResource(R.string.trash_open_count, trashCount))
        }
        Text(
            when {
                searchPending -> stringResource(R.string.search_in_progress)
                searching -> stringResource(R.string.search_count, visible.size)
                else -> stringResource(R.string.notes_count, visible.size)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        if (hasMore) Text(
            stringResource(R.string.search_more, NoteStore.SEARCH_VISIBLE_LIMIT),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FilledTonalButton(onClick = onCreate, enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("new-note")) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.new_note))
        }
        if (searchPending) {
            Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.search_in_progress))
            }
        } else if (visible.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(if (searching) R.string.search_empty else R.string.empty_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                if (!searching) Text(stringResource(R.string.empty_description), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f).testTag("note-list"), contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visible, key = { it.id }) { item ->
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


@Composable
private fun TrashList(
    notes: List<NoteSummary>, busy: Boolean, onBack: () -> Unit,
    onRestore: (String) -> Unit, onPermanent: (String) -> Unit, modifier: Modifier
) {
    Column(modifier.fillMaxHeight().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.trash_title),
                style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack, enabled = !busy,
                modifier = Modifier.testTag("back-from-trash")) {
                Text(stringResource(R.string.back))
            }
        }
        Text(stringResource(R.string.trash_count, notes.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (notes.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.trash_empty), modifier = Modifier.testTag("trash-empty"))
            }
        } else {
            LazyColumn(Modifier.weight(1f).testTag("trash-list"),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(notes, key = { it.id }) { item ->
                    Card(shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.title.ifBlank { stringResource(R.string.untitled) },
                                style = MaterialTheme.typography.titleMedium)
                            if (item.preview.isNotBlank()) Text(item.preview, maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium)
                            Row(Modifier.fillMaxWidth().testTag("trash-actions-" + item.id),
                                horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { onRestore(item.id) }, enabled = !busy,
                                    modifier = Modifier.testTag("trash-restore")) {
                                    Text(stringResource(R.string.trash_restore))
                                }
                                TextButton(onClick = { onPermanent(item.id) }, enabled = !busy,
                                    modifier = Modifier.testTag("trash-permanent")) {
                                    Text(stringResource(R.string.trash_permanent))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
