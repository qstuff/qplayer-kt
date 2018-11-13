package org.qstuff.qplayer.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver

import org.qstuff.qplayer.R

import timber.log.Timber

/*
 * Created by Claus Chierici (github@antamauna.net) on 2/19/15
 *
 * Copyright (C) 2015, 2016, 2017 Claus Chierici, All rights reserved.
 */

class CuepointView : View {

    private var cuepointIndicator: Paint? = null
    private var cuepointIndicatorPath: Path? = null

    private var a: Point? = null
    private var b: Point? = null
    private var c: Point? = null

    private var currentX: Float = 0.toFloat()
    private var sideMargin: Float = 0.toFloat()
    private var waveFormWidth: Int = 0
    private var viewHeight: Int = 0
    private var markerWidth: Float = 0.toFloat()
    private var markerTopMargin: Float = 0.toFloat()
    var cuepointPosition: Int = 0

        set(position) {
            Timber.d("setCuepointPosition(): %d", position)
            field = position
            if (position < 0) return

            currentX = sideMargin + position * stretchFactor

            Timber.d("setCuepointPosition(): current X: %f", currentX)

            cuepointIndicatorPath!!.reset()

            a = Point((currentX - markerWidth / 2).toInt(), -markerTopMargin.toInt())
            b = Point((currentX + markerWidth / 2).toInt(), -markerTopMargin.toInt())
            c = Point(currentX.toInt(), viewHeight / 3)

            invalidate()
        }

    private var stretchFactor = 1.0f


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
        cuepointIndicator!!.color = resources.getColor(R.color.q_orange)
        cuepointIndicator!!.style = Paint.Style.FILL
        cuepointIndicator!!.strokeWidth = 1f

        sideMargin = resources.getDimension(R.dimen.waveform_side_margin)
        markerWidth = resources.getDimension(R.dimen.cue_marker_width)
        markerTopMargin = resources.getDimension(R.dimen.cue_marker_top_margin)

        cuepointIndicatorPath = Path()
        cuepointIndicatorPath!!.fillType = Path.FillType.EVEN_ODD

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
        Timber.d("setupDimensions(): %d", height)

        viewHeight = height
        waveFormWidth = (width - 2 * sideMargin).toInt()

        Timber.d("setupDimensions(): waveFormWidth: %d", waveFormWidth)

        stretchFactor = waveFormWidth.toFloat() / 1000

        Timber.d("setupDimensions(): stretchFactor: %f", stretchFactor)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Timber.v("onDraw():")

        if (a == null || b == null || c == null) {
            Timber.w("onDraw(): something wrong")
            return
        }

        Timber.v("onDraw(): a: %s", a!!.toString())
        Timber.v("onDraw(): b: %s", b!!.toString())
        Timber.v("onDraw(): c: %s", c!!.toString())

        cuepointIndicatorPath!!.moveTo(a!!.x.toFloat(), a!!.y.toFloat())
        cuepointIndicatorPath!!.lineTo(b!!.x.toFloat(), b!!.y.toFloat())
        cuepointIndicatorPath!!.lineTo(c!!.x.toFloat(), c!!.y.toFloat())
        cuepointIndicatorPath!!.lineTo(a!!.x.toFloat(), a!!.y.toFloat())
        cuepointIndicatorPath!!.close()

        canvas.drawPath(cuepointIndicatorPath!!, cuepointIndicator!!)
    }
}
