package io.github.lihuihnu.xuaneynote.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.lihuihnu.xuaneynote.R
import io.github.lihuihnu.xuaneynote.data.AttachmentEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * B3.3 开发态功能闸门。未完成用户手工 SAF/Photo Picker 验收及最终数据安全审查前，
 * 常规应用内绝不开放插图或删图入口。单独 composable 仪器测试可验证面板交互，
 * 但不能靠“组件测试通过”替代真实用户操作或签名升级。
 */
internal object ImageUiGate {
    const val USER_ENTRY_ENABLED = false
}

/** 只从 APP 私有文件获取低分辨率图，实际解码与 SHA-256 验证由 data 模块在 IO 线程完成。 */
@Composable
private fun ImagePreview(
    item: AttachmentEntity,
    maxEdge: Int,
    loader: suspend (String, Int) -> Bitmap,
    modifier: Modifier = Modifier
) {
    var loadFailed by remember(item.id, item.sha256, maxEdge) { mutableStateOf(false) }
    val thumbnail by produceState<Bitmap?>(null, item.id, item.sha256, maxEdge) {
        value = try {
            withContext(Dispatchers.IO) { loader(item.id, maxEdge) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadFailed = true
            null
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = "本机图片缩略图",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().testTag("image-loaded")
            )
        } else {
            Text(
                stringResource(if (loadFailed) R.string.image_load_failed else R.string.image_loading),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/**
 * 可单独 Compose 测试的图片列表，不依赖外部 URI，不持久化 Bitmap 至 SavedState。
 * 正式 NoteApp 目前由 USER_ENTRY_ENABLED=false 隐藏，解除须另一次人工验收和代码审查。
 */
@Composable
internal fun NoteImageStrip(
    attachments: List<AttachmentEntity>,
    busy: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    loadImage: suspend (String, Int) -> Bitmap,
    modifier: Modifier = Modifier
) {
    var pendingRemove by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier.testTag("note-image-panel"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.note_images_title),
            style = MaterialTheme.typography.titleSmall)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 126.dp).testTag("note-images")
        ) {
            items(attachments, key = { it.id }) { item ->
                Column(Modifier.width(116.dp)) {
                    Card(onClick = { expandedId = item.id },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(112.dp).testTag("note-image-" + item.id)) {
                        ImagePreview(item, 224, loadImage, Modifier.fillMaxSize())
                    }
                    TextButton(
                        onClick = { pendingRemove = item.id },
                        enabled = !busy,
                        modifier = Modifier.testTag("remove-note-image-" + item.id)
                    ) {
                        Text(stringResource(R.string.image_remove))
                    }
                }
            }
            item {
                FilledTonalButton(
                    onClick = onAdd,
                    enabled = !busy,
                    modifier = Modifier.width(112.dp).height(96.dp).testTag("add-note-image")
                ) {
                    Text(stringResource(R.string.image_add))
                }
            }
        }
        Text(
            stringResource(R.string.image_privacy_notice),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val removeId = pendingRemove
    if (removeId != null) {
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(stringResource(R.string.image_remove_title)) },
            text = { Text(stringResource(R.string.image_remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemove = null
                    onRemove(removeId)
                }, enabled = !busy, modifier = Modifier.testTag("confirm-remove-image")) {
                    Text(stringResource(R.string.image_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null },
                    modifier = Modifier.testTag("cancel-remove-image")) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    val expanded = attachments.firstOrNull { it.id == expandedId }
    if (expanded != null) {
        AlertDialog(
            onDismissRequest = { expandedId = null },
            title = { Text(stringResource(R.string.image_preview_title)) },
            text = {
                ImagePreview(expanded, 768, loadImage,
                    Modifier.fillMaxWidth().heightIn(min = 240.dp).testTag("image-preview-expanded"))
            },
            confirmButton = {
                TextButton(onClick = { expandedId = null }) {
                    Text(stringResource(R.string.done))
                }
            }
        )
    }
}
