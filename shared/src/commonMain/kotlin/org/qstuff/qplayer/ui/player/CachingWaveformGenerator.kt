package org.qstuff.qplayer.ui.player

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

private val log = Logger.withTag("CachingWaveformGenerator")

/**
 * Keeps generated waveforms as small files (one per track, ~1 KB overview + ~5 KB detail per
 * minute) in [cacheDir], so a track
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

    override suspend fun generate(uri: String, onProgress: (Waveform) -> Unit): Waveform? {
        val file = Path(cacheDir, cacheFileName(uri, withContext(Dispatchers.Default) { fileSize(uri) }))
        withContext(Dispatchers.Default) { read(file) }?.let { return it }
        val bytes = delegate.generate(uri, onProgress) ?: return null
        withContext(Dispatchers.Default) { write(file, bytes) }
        return bytes
    }

    private fun read(file: Path): Waveform? = try {
        if (SystemFileSystem.metadataOrNull(file)?.isRegularFile != true) null
        else SystemFileSystem.source(file).buffered().use { decode(it.readByteArray()) }
    } catch (e: Exception) {
        log.w(e) { "can't read $file" }
        null
    }

    private fun write(file: Path, waveform: Waveform) {
        try {
            SystemFileSystem.createDirectories(Path(cacheDir))
            // Write to a temp file and move it in place, so a cut-off write is never read back.
            val temp = Path(cacheDir, "${file.name}.tmp")
            SystemFileSystem.sink(temp).buffered().use { it.write(encode(waveform)) }
            SystemFileSystem.atomicMove(temp, file)
        } catch (e: Exception) {
            log.w(e) { "can't write $file" }
        }
    }

    internal companion object {
        /** File format: "QWF2", detail peaks per second (float bits), overview size, detail size, data. */
        private const val MAGIC = 0x51574632   // "QWF2"

        /** "<64-bit FNV-1a hash of id + size, hex>.wf2" (".wf" files of the overview-only format are ignored) */
        fun cacheFileName(uri: String, size: Long?): String {
            var hash = -0x340d631b7bdddcdbL   // FNV-1a 64-bit offset basis
            for (ch in "$uri|${size ?: -1}") {
                hash = (hash xor ch.code.toLong()) * 0x100000001b3L
            }
            return hash.toULong().toString(16).padStart(16, '0') + ".wf2"
        }

        fun encode(waveform: Waveform): ByteArray {
            val buffer = Buffer()
            buffer.writeInt(MAGIC)
            buffer.writeInt(waveform.detailPeaksPerSecond.toRawBits())
            buffer.writeInt(waveform.overview.size)
            buffer.writeInt(waveform.detail.size)
            buffer.write(waveform.overview)
            buffer.write(waveform.detail)
            return buffer.readByteArray()
        }

        /** null if [bytes] isn't a complete file of this format. */
        fun decode(bytes: ByteArray): Waveform? {
            if (bytes.size < 16) return null
            val buffer = Buffer().apply { write(bytes) }
            if (buffer.readInt() != MAGIC) return null
            val rate = Float.fromBits(buffer.readInt())
            val overviewSize = buffer.readInt()
            val detailSize = buffer.readInt()
            if (overviewSize <= 0 || detailSize < 0 || buffer.size != overviewSize.toLong() + detailSize) return null
            return Waveform(buffer.readByteArray(overviewSize), buffer.readByteArray(detailSize), rate)
        }
    }
}
