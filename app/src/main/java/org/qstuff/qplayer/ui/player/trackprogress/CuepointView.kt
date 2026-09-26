package org.qstuff.qplayer.ui.player.trackprogress

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat

import org.qstuff.qplayer.R

import timber.log.Timber

/*
 * Created by Claus Chierici (github@antamauna.net) on 2/19/15
 *
 * Copyright (C) 2015, 2016, 2017 Claus Chierici, All rights reserved.
 */

class CuepointView : View {

    private lateinit var cuepointIndicator: Paint
    private lateinit var cuepointIndicatorPath: Path

    private lateinit var a: Point
    private lateinit var b: Point
    private lateinit var c: Point
    private lateinit var d: Point
    private lateinit var e: Point
    private lateinit var f: Point


    private var currentX: Float = 0.toFloat()
    private var sideMargin: Float = 0.toFloat()
    private var waveFormWidth: Int = 0
    private var viewHeight: Int = 0
    private var markerWidth: Float = 0.toFloat()
    private var markerTopMargin: Float = 0.toFloat()

    var cuepointPosition: Int = 0

        set(position) {
            field = position
            if (position < 0) return
            currentX = (sideMargin + waveFormWidth * (field.toFloat() / 1000) - 2)
            cuepointIndicatorPath.reset()

            a = Point((currentX - markerWidth / 2).toInt(), - markerTopMargin.toInt())
            b = Point((currentX + markerWidth / 2).toInt(), - markerTopMargin.toInt())
            c = Point(currentX.toInt() +1, viewHeight / 5)
            d = Point(currentX.toInt() +1 , viewHeight + markerTopMargin.toInt())
            e = Point(currentX.toInt() -1, viewHeight + markerTopMargin.toInt())
            f = Point(currentX.toInt() -1, viewHeight / 5)

            invalidate()
        }

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {

        cuepointIndicator = Paint(Paint.ANTI_ALIAS_FLAG)
        cuepointIndicator.color = ContextCompat.getColor(context, R.color.q_orange)
        cuepointIndicator.style = Paint.Style.FILL
        cuepointIndicator.strokeWidth = 2f

        sideMargin = resources.getDimension(R.dimen.waveform_side_margin)
        markerWidth = resources.getDimension(R.dimen.cue_marker_width)
        markerTopMargin = resources.getDimension(R.dimen.cue_marker_top_margin)
        viewHeight = resources.getDimension(R.dimen.seekbar_height).toInt()

        cuepointIndicatorPath = Path()
        cuepointIndicatorPath.fillType = Path.FillType.WINDING

        val viewTreeObserver = viewTreeObserver
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    getViewTreeObserver().removeOnGlobalLayoutListener(this)
                    setupDimensions()
                }
            })
        }
    }

    private fun setupDimensions() {
        waveFormWidth = (width - 2 * sideMargin).toInt()

        Timber.v("setupDimensions(): marker w: $markerWidth, h: $viewHeight")
        Timber.v("setupDimensions(): waveFormWidth: $waveFormWidth")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Timber.v("onDraw():")

        cuepointIndicatorPath.run {
            moveTo(a.x.toFloat(), a.y.toFloat())
            lineTo(b.x.toFloat(), b.y.toFloat())
            lineTo(c.x.toFloat(), c.y.toFloat())
            lineTo(d.x.toFloat(), d.y.toFloat())
            lineTo(e.x.toFloat(), e.y.toFloat())
            lineTo(f.x.toFloat(), f.y.toFloat())
            lineTo(a.x.toFloat(), a.y.toFloat())
            close()
        }

        canvas.drawPath(cuepointIndicatorPath, cuepointIndicator)
    }
}
