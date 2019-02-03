package org.qstuff.qplayer.player.service


interface PitchControlListener {

    fun onPitchControlChanged(progress: Int, fromJog: Boolean)
    fun onStartPitchChange()
    fun onStopPitchChange()
}
