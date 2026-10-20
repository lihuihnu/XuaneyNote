package io.github.lihuihnu.xuaneynote.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lihuihnu.xuaneynote.model.ThemeMode
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * 使用 API29+ 系统 MediaStore Downloads 的本应用创建内容 URI。
 * 仅合成文件，finally 删除；不能替代用户手工点击 SAF CreateDocument 的验收。
 */
@RunWith(AndroidJUnit4::class)
class BundleContentResolverRoundTripTest {
    @Test fun systemContentProviderSupportsClosedZipReadback() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val resolver = ctx.contentResolver
        val name = "XuaneyNote-ci-synthetic-" + UUID.randomUUID().toString() + ".zip"
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }) ?: error("模拟系统提供者未允许创建私有测试文件")
        try {
            val note = NoteEntity("00000000-0000-0000-0000-000000000701",
                "Android ContentResolver 合成资料🙂", "导出后关闭，再打开读回",
                100, 200, 3, "plain", 1, 300)
            val original = BundleSnapshot(listOf(note), emptyList(), emptyList(),
                ThemeMode.LIGHT, 999L)
            requireNotNull(resolver.openOutputStream(uri, "wt")).use {
                BundleBackupV2.write(it, original) { error("不含图片") }
            }
            val verified = requireNotNull(resolver.openInputStream(uri)).use {
                BundleBackupV2.inspect(it, ctx.cacheDir)
            }
            verified.use {
                assertEquals(original, it.snapshot)
                assertEquals(300L, it.snapshot.notes.single().deletedAt)
            }
        } finally {
            assertEquals(1, resolver.delete(uri, null, null))
        }
    }
}
