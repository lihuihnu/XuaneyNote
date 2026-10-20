package io.github.lihuihnu.xuaneynote.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.lihuihnu.xuaneynote.data.b5.Zip4jInteropProbe
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** B5.1: test-only Zip4j, no dependency or encryption UI in the production APK. */
@RunWith(AndroidJUnit4::class)
class B5Zip4jCompatibilityTest {
    private fun sandbox(block: (File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "b5-synthetic-" + UUID.randomUUID())
        check(folder.mkdir())
        try { block(folder) } finally { folder.deleteRecursively() }
    }

    @Test fun aes256UnicodeRoundTripAndWrongPasswordFailClosed() = sandbox { folder ->
        val payload = File(folder, "payload.xuaney.zip")
        val original = "合成文本、换行\nEnglish and Emoji 🧪🌙\n".repeat(32)
            .toByteArray(Charsets.UTF_8)
        payload.writeBytes(original)
        val password = "Unicode测试🧪-synthetic-pass-2026".toCharArray()
        val wrong = "Wrong-long-synthetic-pass-2026".toCharArray()
        try {
            val zipped = File(folder, "aes.zip")
            Zip4jInteropProbe.writeAes(payload, zipped, password)
            assertArrayEquals(original, Zip4jInteropProbe.readAes(zipped, password))
            assertTrue(runCatching { Zip4jInteropProbe.readAes(zipped, wrong) }.isFailure)
            assertTrue(runCatching { Zip4jInteropProbe.writeAes(payload,
                File(folder, "empty.zip"), charArrayOf()) }.isFailure)
        } finally {
            password.fill('\u0000')
            wrong.fill('\u0000')
        }
    }

    @Test fun authenticatedMacTamperAndTruncationMustBeRejected() = sandbox { folder ->
        val payload = File(folder, "payload.xuaney.zip").apply {
            writeBytes(ByteArray(4096) { i -> (i % 251).toByte() })
        }
        val password = "Synthetic🧪-integrity-test-2026".toCharArray()
        try {
            val original = File(folder, "good.zip")
            Zip4jInteropProbe.writeAes(payload, original, password)
            val damaged = File(folder, "mac-corrupted.zip")
            Zip4jInteropProbe.corruptMac(original, damaged)
            Zip4jInteropProbe.mustReject(damaged, password)
            val cut = File(folder, "truncated.zip")
            val raw = original.readBytes()
            cut.writeBytes(raw.copyOf(raw.size - 12))
            Zip4jInteropProbe.mustReject(cut, password)
        } finally { password.fill('\u0000') }
    }

    @Test fun plaintextLegacyAndMultiEntryAreStrictlyRejected() = sandbox { folder ->
        val payload = File(folder, "payload.xuaney.zip").apply {
            writeText("仅用于封装格式测试", Charsets.UTF_8)
        }
        val password = "Synthetic🧪-format-gate-2026".toCharArray()
        try {
            val plain = File(folder, "plain.zip")
            Zip4jInteropProbe.writePlain(payload, plain)
            Zip4jInteropProbe.mustReject(plain, password)
            val aes128 = File(folder, "aes128.zip")
            Zip4jInteropProbe.writeAes128(payload, aes128, password)
            Zip4jInteropProbe.mustReject(aes128, password)
            val legacy = File(folder, "zipcrypto.zip")
            Zip4jInteropProbe.writeLegacy(payload, legacy, password)
            Zip4jInteropProbe.mustReject(legacy, password)
            val multiple = File(folder, "multiple.zip")
            Zip4jInteropProbe.writeTwoEntries(payload, multiple, password)
            Zip4jInteropProbe.mustReject(multiple, password)
        } finally { password.fill('\u0000') }
    }
}
