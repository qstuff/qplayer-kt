package org.qstuff.qplayer.ui.player

import kotlinx.coroutines.CoroutineScope

/**
 * Vinyl-style scratching for the jog wheel's SCRATCH mode, as the player sees it — platform
 * independent. While the wheel is touched it plays the track itself (the regular player is paused);
 * see the Android ScratchEngine for how.
 */
interface Scratcher {

    val isScratching: Boolean

    /** The read head's position while scratching / where the last scratch ended. */
    val positionMs: Long

    /**
     * Get the track at [uri] ready for scratching (in [scope], in the background). The decode also
     * yields the track's overview waveform (as [WaveformGenerator] would), so in scratch mode the
     * track is decoded only once: [onWaveform] receives it (null if the track can't be decoded) on
     * [scope]'s dispatcher when the decode is done — right away if [uri] is already decoded. A
     * later call for the same [uri] replaces a still pending [onWaveform]; a call for another
     * track or [release] drops it.
     */
    fun prepare(scope: CoroutineScope, uri: String, onWaveform: ((ByteArray?) -> Unit)? = null)

    /** Stop scratching and drop the prepared track. */
    fun release()

    /**
     * Start scratching at [positionMs]; false if not possible (yet), e.g. the track isn't prepared
     * up to there. [sensitivity] (jog wheel setting, 10 = default) scales the audio per rotation.
     */
    fun start(positionMs: Long, sensitivity: Int): Boolean

    /** Move the record: [rotationRad] is the wheel's rotation since touch-down (clockwise > 0). */
    fun scratchTo(rotationRad: Double)

    /** Stop scratching; returns the final position in ms. */
    fun stop(): Long
}
