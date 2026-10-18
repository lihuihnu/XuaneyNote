package io.github.lihuihnu.xuaneynote.data

import android.content.Context
import io.github.lihuihnu.xuaneynote.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files

/**
 * B5.2 internal backend, NOT a UI entry point. Wraps a complete, B4-validated
 * v2 ZIP in exactly one authenticated AES-256 ZIP entry. Outer metadata is still
 * partly visible and standard WinZip AES uses a weak 1000-round password KDF.
 *
 * Import NEVER writes the Room database: it first requires AES validation to EOF
 * followed by the existing B4 full ZIP/content inspection. Only a separate
 * explicit call to restore() is allowed to commit through the B4 transaction.
 */
class EncryptedBundleTransferService(
    context: Context,
    private val original: BundleTransferService
) {
    companion object {
        private const val INNER_NAME = "payload.xuaney.zip"
        private const val STAGE_PREFIX = "xuaney-b5-"
        private const val MAX_INNER = BundleBackupV2.MAX_COMPRESSED_BYTES
        // STORED outer entry adds header, salt, verifier, MAC and central directory.
        private const val MAX_OUTER = BundleBackupV2.MAX_COMPRESSED_BYTES + 2L * 1024 * 1024
        private val gate = Mutex()
        private val ownedDirName = Regex("xuaney-b5-[a-zA-Z0-9_-]+")
    }
    private val appContext = context.applicationContext

    private fun validatePassword(password: CharArray) {
        // This is a minimum input bound, not a password-strength proof.
        require(password.size in 12..1024) { "密码长度不符合加密备份要求" }
    }

    /** Neither log nor persist passwords; the caller retains ownership of its input array. */
    private suspend fun <T> temporaryOperation(block: suspend (File) -> T): T =
        withContext(Dispatchers.IO) {
            gate.withLock {
                val base = appContext.cacheDir.canonicalFile
                val directory = Files.createTempDirectory(
                    base.toPath(), STAGE_PREFIX
                ).toFile()
                require(directory.canonicalFile.parentFile == base &&
                    !Files.isSymbolicLink(directory.toPath()))
                try {
                    block(directory)
                } finally {
                    // Cancellation must not leave cleartext staging in ordinary error paths.
                    withContext(NonCancellable) {
                        check(directory.deleteRecursively()) {
                            "加密备份临时文件清理失败"
                        }
                    }
                }
            }
        }

    private suspend fun copyBounded(
        source: InputStream,
        destination: OutputStream,
        limit: Long
    ): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        var emptyReads = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = source.read(buffer)
            if (n < 0) break
            if (n == 0) {
                check(++emptyReads < 128) { "文件提供方持续返回空字节" }
                continue
            }
            emptyReads = 0
            total += n
            require(total <= limit) { "加密备份文件超过容量限制" }
            destination.write(buffer, 0, n)
        }
        return total
    }

    private suspend fun writeVerifiedFile(
        source: InputStream, target: File, maxBytes: Long
    ) {
        source.use { stream ->
            FileOutputStream(target).use { output ->
                require(copyBounded(stream, output, maxBytes) > 0L) {
                    "空备份文件不允许恢复"
                }
                output.fd.sync()
            }
        }
    }

    private fun zipParameters() = ZipParameters().apply {
        setFileNameInZip(INNER_NAME)
        setEncryptFiles(true)
        setEncryptionMethod(EncryptionMethod.AES)
        setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256)
        // The inner B4 ZIP is compressed already: no redundant second deflate.
        setCompressionMethod(CompressionMethod.STORE)
    }

    /**
     * Export to a caller-owned stream, without changing notes or image references.
     * B5.3 must still SAF-close, reopen and verify the actually written URI.
     */
    suspend fun export(
        output: OutputStream,
        theme: ThemeMode,
        password: CharArray,
        at: Long = System.currentTimeMillis()
    ): BundleSnapshot {
        validatePassword(password)
        return temporaryOperation { directory ->
            val inner = File(directory, INNER_NAME)
            val envelope = File(directory, "envelope.aes.zip")
            val snapshot = FileOutputStream(inner).use { original.export(it, theme, at) }
            require(inner.length() in 1..MAX_INNER) { "完整备份超出限制" }
            val localPassword = password.copyOf()
            try {
                ZipFile(envelope, localPassword).use { zip ->
                    zip.addFile(inner, zipParameters())
                }
            } finally {
                localPassword.fill('\u0000')
            }
            require(envelope.length() in 1..MAX_OUTER) { "加密文件超出上限" }
            FileInputStream(envelope).use { stream ->
                check(copyBounded(stream, output, MAX_OUTER) == envelope.length()) {
                    "写出加密备份时字节数发生变化"
                }
            }
            output.flush()
            snapshot
        }
    }

    /**
     * Decrypt into a bounded private staging file, fully reading to EOF to verify
     * AES MAC. Reject split archives, ZipCrypto, AES-128, plain/multiple/renamed
     * entries and excessive size BEFORE passing bytes to B4's strict inspector.
     */
    suspend fun inspect(input: InputStream, password: CharArray): InspectedBundle {
        validatePassword(password)
        return temporaryOperation { directory ->
            val envelope = File(directory, "incoming.aes.zip")
            writeVerifiedFile(input, envelope, MAX_OUTER)
            val inner = File(directory, INNER_NAME)
            val localPassword = password.copyOf()
            try {
                ZipFile(envelope, localPassword).use { zip ->
                    val entries = zip.fileHeaders
                    require(!zip.isSplitArchive && entries.size == 1) {
                        "加密备份必须恰好有一个文件"
                    }
                    val header = entries.single()
                    require(header.fileName == INNER_NAME && !header.isDirectory &&
                        header.isEncrypted &&
                        header.encryptionMethod == EncryptionMethod.AES &&
                        header.aesExtraDataRecord?.aesKeyStrength ==
                            AesKeyStrength.KEY_STRENGTH_256) {
                        "加密备份不是受支持的 AES-256 单条目格式"
                    }
                    require(header.uncompressedSize in 1..MAX_INNER &&
                        header.compressedSize in 1..MAX_OUTER) {
                        "加密备份大小不符合限制"
                    }
                    zip.getInputStream(header).use { decrypted ->
                        writeVerifiedFile(decrypted, inner, MAX_INNER)
                    }
                }
            } finally {
                localPassword.fill('\u0000')
            }
            // B4 creates an independent inspected staging directory. The outer
            // AES staging folder is deleted before the inspected result is returned.
            original.inspect(FileInputStream(inner))
        }
    }

    /** Only explicit user confirmation in B5.3 may invoke this B4 transaction. */
    suspend fun restore(verified: InspectedBundle): BundleRestoreResult =
        original.restore(verified)

    /** On process restart remove only this feature's own abandoned private dirs. */
    suspend fun cleanupOrphans(): Int = withContext(Dispatchers.IO) {
        gate.withLock {
            val base = appContext.cacheDir.canonicalFile
            var count = 0
            for (entry in base.listFiles().orEmpty()) {
                if (ownedDirName.matches(entry.name) &&
                    entry.isDirectory &&
                    !Files.isSymbolicLink(entry.toPath()) &&
                    entry.canonicalFile.parentFile == base) {
                    if (entry.deleteRecursively()) count++
                }
            }
            count
        }
    }
}
