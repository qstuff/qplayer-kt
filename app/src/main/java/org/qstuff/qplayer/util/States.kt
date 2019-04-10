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