package org.qstuff.qplayer.ui.player.waveform

import android.content.Context
import org.qstuff.qplayer.ui.player.WaveformGenerator

/** Android [WaveformGenerator]: decodes with MediaExtractor + MediaCodec (see [WaveformAnalyzer]). */
class AndroidWaveformGenerator(private val context: Context) : WaveformGenerator {
    override suspend fun generate(uri: String): ByteArray? = WaveformAnalyzer.analyze(context, uri)
}
