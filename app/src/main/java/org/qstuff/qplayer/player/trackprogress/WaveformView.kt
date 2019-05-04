package org.qstuff.qplayer.player.trackprogress

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat

import org.qstuff.qplayer.R
import org.qstuff.qplayer.datasource.model.TrackData
import timber.log.Timber


/**
 * Created by Claus Chierici (chierici@karlmax-berlin.com) on 5/12/17
 * for Karlmax Berlin GmbH & Co. KG
 *
 * Copyright (C) 2013, 2014, 2015 Karlmax Berlin GmbH & Co. KG,
 * All rights reserved.
 */
class WaveformView : View {

    private lateinit var background: Paint
    private lateinit var bgRect: RectF
    private lateinit var segment: Paint
    private lateinit var waveFormUpper: Paint
    private lateinit var waveFormLower: Paint
    private lateinit var waveFormUpperDefault: Paint
    private lateinit var waveFormLowerDefault: Paint
    private lateinit var centerLine: Paint
    private lateinit var zeroDBLine: Paint

    private var sideMargin = 0.0f
    private var topMargin = 0.0f
    private var zeroDBOffset = 0.0f
    private var waveFormWidth = 0.0f
    private var waveFormHeight = 0.0f
    private var viewHeight = 0.0f
    private var waveFormCenterY = 0.0f
    private var stretchFactor = 1.0f

    private var waveformData: TrackData? = null

    constructor(context: Context) : super(context) { init() }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { init() }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { init() }

    private fun init() {

        background = Paint(Paint.ANTI_ALIAS_FLAG)
        background.color = ContextCompat.getColor(context, R.color.black)

        segment = Paint(Paint.ANTI_ALIAS_FLAG)
        segment.color = ContextCompat.getColor(context, R.color.white)

        waveFormUpper = Paint(Paint.ANTI_ALIAS_FLAG)
        waveFormUpper.color = ContextCompat.getColor(context, R.color.white)
        waveFormLower = Paint(Paint.ANTI_ALIAS_FLAG)
        waveFormLower.color = ContextCompat.getColor(context, R.color.white)

        waveFormUpperDefault = Paint(Paint.ANTI_ALIAS_FLAG)
        waveFormUpperDefault.color = ContextCompat.getColor(context, R.color.black)
        waveFormLowerDefault = Paint(Paint.ANTI_ALIAS_FLAG)
        waveFormLowerDefault.color = ContextCompat.getColor(context, R.color.black)

        centerLine = Paint(Paint.ANTI_ALIAS_FLAG)
        centerLine.color = ContextCompat.getColor(context, R.color.q_orange)
        centerLine.strokeWidth = 1f

        zeroDBLine = Paint(Paint.ANTI_ALIAS_FLAG)
        zeroDBLine.color = ContextCompat.getColor(context, R.color.white)
        zeroDBLine.strokeWidth = 1f

        sideMargin = resources.getDimension(R.dimen.rounded_shape_radius)
        topMargin = resources.getDimension(R.dimen.waveform_top_margin)
        zeroDBOffset = topMargin + 10

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
        Timber.v("setupDimensions(): %d", height)

        viewHeight = height.toFloat()

        bgRect = RectF(sideMargin, 0f, width - sideMargin, viewHeight.toFloat())

        waveFormWidth = width - 2 * sideMargin
        waveFormHeight = viewHeight - 2 * topMargin
        waveFormCenterY = viewHeight / 2

        Timber.v("setupDimensions(): view width:           $width")
        Timber.v("setupDimensions(): waveFormWidth width:  $waveFormWidth")
        Timber.v("setupDimensions(): view height:          $viewHeight")
        Timber.v("setupDimensions(): waveFormHeight width: $waveFormHeight")
    }

    fun updateWaveform(data: TrackData?) {
        Timber.d("updateWaveform():")

        if (data?.bytes == null) {
            waveformData = null
            stretchFactor = 1.0f
            invalidate()
            return
        }

        Timber.d("updateWaveform(): num samples:  ${data.bytes?.size}")
        Timber.d("updateWaveform(): width pixels: $waveFormWidth")

        waveformData = data

        if (waveFormWidth > data.bytes!!.size) {
            stretchFactor = waveFormWidth / data.bytes!!.size
        } else {
            stretchFactor = data.bytes!!.size.toFloat() / waveFormWidth
        }

        Timber.v("updateWaveform(): stretchFactor: %f", stretchFactor)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(bgRect, background)
        canvas.drawLine(sideMargin,
                waveFormCenterY,
                sideMargin + waveFormWidth,
                waveFormCenterY,
                centerLine)


        // DEBUG or maybe feature: the 0db lines

        /*
        canvas.drawLine(sideMargin,
                        zeroDBOffset,
                        sideMargin + waveFormWidth,
                        zeroDBOffset,
                        zeroDBLine);

        canvas.drawLine(sideMargin,
                        viewHeight - zeroDBOffset,
                        sideMargin + waveFormWidth,
                        viewHeight - zeroDBOffset,
                        zeroDBLine);
*/

        if (waveformData != null) {
            var dbValue: Float

            for (i in 0 until waveformData!!.bytes!!.size) {

                dbValue = waveformData!!.bytes!![i].toFloat() *-3.5f

                if (dbValue > waveFormCenterY - zeroDBOffset) {
                    dbValue = waveFormCenterY
                }

                if (i < waveFormWidth) {

                    canvas.drawLine(sideMargin + i * stretchFactor,
                            zeroDBOffset + dbValue,
                            sideMargin + i * stretchFactor,
                            waveFormCenterY,
                            waveFormUpper)

                    canvas.drawLine(sideMargin + i * stretchFactor,
                            waveFormCenterY,
                            sideMargin + i * stretchFactor,
                            viewHeight - zeroDBOffset - dbValue,
                            waveFormLower)
                }
            }

        } else {

            var i = 0
            while (i < waveFormWidth / stretchFactor) {

                canvas.drawLine(
                        sideMargin + i * stretchFactor,
                        zeroDBOffset,
                        sideMargin + i * stretchFactor,
                        waveFormCenterY,
                        waveFormUpperDefault)

                canvas.drawLine(
                        sideMargin + i * stretchFactor,
                        waveFormCenterY,
                        sideMargin + i * stretchFactor,
                        viewHeight - zeroDBOffset,
                        waveFormLowerDefault)
                i++
            }
        }
    }
}
