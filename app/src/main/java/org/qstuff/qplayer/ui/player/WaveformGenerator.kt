package org.qstuff.qplayer.ui.player

/**
 * Creates the overview waveform of an audio file: one peak amplitude (0..127) per point, or null
 * if the file can't be decoded. Platform-specific decoding (Android: MediaCodec).
 */
fun interface WaveformGenerator {
    suspend fun generate(uri: String): ByteArray?
}
