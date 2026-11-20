package org.qstuff.qplayer.util

/*
 * Created by Claus Chierici (claus@qstuff.org) 
 * on 2/16/19
 * Copyright (C) 2018 until now by Claus Chierici. All rights reserved.
 */

enum class PlayerStatus {
    UNDEFINED,
    PLAYING,
    PAUSED,
    SEEKING,
    ERROR
}

enum class TrackRepeatStatus {
    NONE,
    ALL,
    ONE
}

/**
 * Arrangement of the player screen on phones (Settings). Tablets always use the two-pane layout.
 * MODERN: fader + jog wheel on top, two button rows below. CLASSIC: the original qplayer layout —
 * fader on the left, buttons beside the jog wheel, track info and waveform below.
 */
enum class PlayerLayout {
    MODERN,
    CLASSIC
}

enum class JogwheelMode {
    SPEED_ANGULAR,
    SPEED_VELOCITY,
    SCRATCH
}