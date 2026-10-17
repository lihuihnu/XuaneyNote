package io.github.lihuihnu.xuaneynote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.lihuihnu.xuaneynote.R
import io.github.lihuihnu.xuaneynote.model.Note
import io.github.lihuihnu.xuaneynote.model.SaveState

@Composable
fun NoteEditor(note: Note, state: SaveState, busy: Boolean, onTitle: (String) -> Unit,
    onBody: (String) -> Unit, onRetry: () -> Unit, onTrash: () -> Unit,
    modifier: Modifier = Modifier) {
    // Bundle 只保存光标，不保存长正文，避免旋转时 TransactionTooLargeException。
    var titleCursor by rememberSaveable(note.id) { mutableIntStateOf(0) }
    var bodyCursor by rememberSaveable(note.id) { mutableIntStateOf(0) }
    var title by remember(note.id) { mutableStateOf(TextFieldValue(note.title, TextRange(titleCursor.coerceAtMost(note.title.length)))) }
    var body by remember(note.id) { mutableStateOf(TextFieldValue(note.body, TextRange(bodyCursor.coerceAtMost(note.body.length)))) }
    val status = when (state) {
        is SaveState.Saved -> R.string.saved
        is SaveState.Saving -> R.string.saving
        is SaveState.Failed -> R.string.save_failed
    }
    Column(modifier.fillMaxHeight().imePadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(status), style = MaterialTheme.typography.labelMedium,
                color = if (state is SaveState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("save-state"))
            Row {
                if (state is SaveState.Failed) TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
                TextButton(onClick = onTrash, enabled = !busy,
                    modifier = Modifier.testTag("trash-current-note")) {
                    Text(stringResource(R.string.move_to_trash))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = title, onValueChange = { value ->
            if (value.text.length <= Note.MAX_TITLE) {
                title = value; titleCursor = value.selection.end; onTitle(value.text)
            }
        }, label = { Text(stringResource(R.string.title_hint)) }, singleLine = true, readOnly = busy,
            textStyle = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth().testTag("note-title"))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = body, onValueChange = { value ->
            if (value.text.length <= Note.MAX_BODY) {
                body = value; bodyCursor = value.selection.end; onBody(value.text)
            }
        }, placeholder = { Text(stringResource(R.string.body_hint)) }, readOnly = busy,
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("note-body"))
        Text(stringResource(R.string.preview_stage), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
    }
}
