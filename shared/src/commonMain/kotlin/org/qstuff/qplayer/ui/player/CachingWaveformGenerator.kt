package org.qstuff.qplayer.ui.player

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

private val log = Logger.withTag("CachingWaveformGenerator")

/**
 * Keeps generated waveforms as small files (one per track, ~1 KB) in [cacheDir], so a track
 * loaded again — also after an app restart — gets its waveform at once instead of being decoded
 * again by [delegate].
 *
 * The file name is a hash of the track id and its file size ([fileSize], null if unknown), so a
 * replaced file with a different size is decoded again. Read/write errors only cost a re-decode.
 */
class CachingWaveformGenerator(
    private val delegate: WaveformGenerator,
    private val cacheDir: String,
    private val fileSize: (uri: String) -> Long?
) : WaveformGenerator {

    override suspend fun generate(uri: String, onProgress: (ByteArray) -> Unit): ByteArray? {
        val file = Path(cacheDir, cacheFileName(uri, withContext(Dispatchers.Default) { fileSize(uri) }))
        withContext(Dispatchers.Default) { read(file) }?.let { return it }
        val bytes = delegate.generate(uri, onProgress) ?: return null
        withContext(Dispatchers.Default) { write(file, bytes) }
        return bytes
    }

    private fun read(file: Path): ByteArray? = try {
        if (SystemFileSystem.metadataOrNull(file)?.isRegularFile != true) null
        else SystemFileSystem.source(file).buffered().use { it.readByteArray() }.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        log.w(e) { "can't read $file" }
        null
    }

    private fun write(file: Path, bytes: ByteArray) {
        try {
            SystemFileSystem.createDirectories(Path(cacheDir))
            // Write to a temp file and move it in place, so a cut-off write is never read back.
            val temp = Path(cacheDir, "${file.name}.tmp")
            SystemFileSystem.sink(temp).buffered().use { it.write(bytes) }
            SystemFileSystem.atomicMove(temp, file)
        } catch (e: Exception) {
            log.w(e) { "can't write $file" }
        }
    }

    internal companion object {
        /** "<64-bit FNV-1a hash of id + size, hex>.wf" */
        fun cacheFileName(uri: String, size: Long?): String {
            var hash = -0x340d631b7bdddcdbL   // FNV-1a 64-bit offset basis
            for (ch in "$uri|${size ?: -1}") {
                hash = (hash xor ch.code.toLong()) * 0x100000001b3L
            }
            return hash.toULong().toString(16).padStart(16, '0') + ".wf"
        }
    }
}
