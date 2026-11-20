package org.qstuff.qplayer.ui.player

/**
 * Creates the overview waveform of an audio file: one peak amplitude (0..127) per point, or null
 * if the file can't be decoded. Platform-specific decoding (Android: MediaCodec, iOS: AVAudioFile).
 */
interface WaveformGenerator {
    /**
     * @param onProgress receives growing previews while decoding (same size as the result; the
     *        points not decoded yet are 0), roughly every 2 % of the track. Called on a
     *        background thread, before [generate] returns.
     */
    suspend fun generate(uri: String, onProgress: (ByteArray) -> Unit = {}): ByteArray?
}
