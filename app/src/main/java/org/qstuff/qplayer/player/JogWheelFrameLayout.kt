package org.qstuff.qplayer.player

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Created by Claus Chierici (chierici@karlmax-berlin.com) on 2/8/18
 * for Karlmax Berlin GmbH & Co. KG
 *
 *
 * Copyright (C) 2014 Karlmax Berlin GmbH & Co. KG, All rights reserved.
 */

class JogWheelFrameLayout : FrameLayout {
    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {}

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val height = measuredHeight
        setMeasuredDimension(height, height)
    }

}
