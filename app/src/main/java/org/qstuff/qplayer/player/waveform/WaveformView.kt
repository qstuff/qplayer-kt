package org.qstuff.qplayer.player.waveform

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
 *
 * Copyright (C) 2013, 2014, 2015 Karlmax Berlin GmbH & Co. KG,
 * All rights reserved.
 */

class WaveformView : View {

    private var background: Paint? = null
    private var bgRect: RectF? = null

    private var segment: Paint? = null
    private var waveFormUpper: Paint? = null
    private var waveFormLower: Paint? = null
    private var waveFormUpperDefault: Paint? = null
    private var waveFormLowerDefault: Paint? = null
    private var centerLine: Paint? = null
    private var zeroDBLine: Paint? = null

    private var sideMargin: Float = 0.toFloat()
    private var topMargin: Float = 0.toFloat()
    private var zeroDBOffset: Float = 0.toFloat()

    private var waveFormWidth: Int = 0
    private var waveFormHeight: Int = 0

    private var viewHeight: Int = 0

    private var waveFormCenterY: Int = 0

    private var stretchFactor = 1.0f

    private var data: TrackData? = null

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

        background = Paint(Paint.ANTI_ALIAS_FLAG)
        background!!.color = ContextCompat.getColor(context, R.color.black)

        segment = Paint(Paint.ANTI_ALIAS_FLAG)
        segment!!.color = ContextCompat.getColor(context, R.color.white)

        waveFormUpper = Paint(Paint.ANTI_ALIAS_FLAG)
        waveFormUpper!!.color = ContextCompat.getColor(context, R.color.white)
        waveFormLower = Paint(Paint.ANTI_ALIAS_FLAG)
        waveFormLower!!.color = ContextCompat.getColor(context, R.color.white)

        waveFormUpperDefault = Paint(Paint.ANTI_ALIAS_FLAG)
        waveFormUpperDefault!!.color = ContextCompat.getColor(context, R.color.black)
        waveFormLowerDefault = Paint(Paint.ANTI_ALIAS_FLAG)
        waveFormLowerDefault!!.color = ContextCompat.getColor(context, R.color.black)

        centerLine = Paint(Paint.ANTI_ALIAS_FLAG)
        centerLine!!.color = ContextCompat.getColor(context, R.color.q_orange)
        centerLine!!.strokeWidth = 1f

        zeroDBLine = Paint(Paint.ANTI_ALIAS_FLAG)
        zeroDBLine!!.color = ContextCompat.getColor(context, R.color.white)
        zeroDBLine!!.strokeWidth = 1f

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

        viewHeight = height

        bgRect = RectF(sideMargin, 0f, width - sideMargin, viewHeight.toFloat())

        waveFormWidth = (width - 2 * sideMargin).toInt()
        waveFormHeight = (viewHeight - 2 * topMargin).toInt()
        waveFormCenterY = viewHeight / 2

        Timber.v("setupDimensions(): view width:           %d", width)
        Timber.v("setupDimensions(): waveFormWidth width:  %d", waveFormWidth)
        Timber.v("setupDimensions(): view height:          %d", viewHeight)
        Timber.v("setupDimensions(): waveFormHeight width: %d", waveFormHeight)
    }

    fun updateWaveform(data: TrackData?) {
        Timber.d("updateWaveform():")

        if (data == null) {
            this.data = null
            stretchFactor = 1.0f
            invalidate()
            return
        }

        Timber.v("updateWaveform(): num samples:  %d", data.bytes.size)
        Timber.v("updateWaveform(): width pixels: %d", waveFormWidth)

        this.data = data

        if (waveFormWidth > data.bytes.size)
            stretchFactor = waveFormWidth.toFloat() / data.bytes.size
        else
            stretchFactor = data.bytes.size as Float / waveFormWidth

        Timber.v("updateWaveform(): stretchFactor: %f", stretchFactor)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(bgRect!!, background!!)
        canvas.drawLine(sideMargin,
                waveFormCenterY.toFloat(),
                sideMargin + waveFormWidth,
                waveFormCenterY.toFloat(),
                centerLine!!)


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

        if (data != null) {
            var dbValue: Int

            for (i in 0 until data!!.bytes.size) {

                dbValue = data!!.bytes[i] * -5

                if (dbValue > waveFormCenterY - zeroDBOffset) {
                    dbValue = waveFormCenterY
                }

                if (i < waveFormWidth) {

                    canvas.drawLine(sideMargin + i * stretchFactor,
                            zeroDBOffset + dbValue,
                            sideMargin + i * stretchFactor,
                            waveFormCenterY.toFloat(),
                            waveFormUpper!!)

                    canvas.drawLine(sideMargin + i * stretchFactor,
                            waveFormCenterY.toFloat(),
                            sideMargin + i * stretchFactor,
                            viewHeight.toFloat() - zeroDBOffset - dbValue.toFloat(),
                            waveFormLower!!)
                }
            }

        } else {

            var i = 0
            while (i < waveFormWidth / stretchFactor) {

                canvas.drawLine(sideMargin + i * stretchFactor,
                        zeroDBOffset,
                        sideMargin + i * stretchFactor,
                        waveFormCenterY.toFloat(),
                        waveFormUpperDefault!!)

                canvas.drawLine(sideMargin + i * stretchFactor,
                        waveFormCenterY.toFloat(),
                        sideMargin + i * stretchFactor,
                        viewHeight - zeroDBOffset,
                        waveFormLowerDefault!!)
                i++
            }
        }
    }
}
