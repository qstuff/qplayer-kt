package org.qstuff.qplayer.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

import org.qstuff.qplayer.R

import timber.log.Timber

/**
 * Created by Claus Chierici
 * Copyright (C) 2016
 * All rights reserved.
 *
 * Parts of this code from: http://go-lambda.blogspot.de/2012/02/rotary-knob-widget-on-android.html
 */
class JogWheelBackgroundView : View {

    // Drawing the wheel
    private var backGround: Paint? = null
    private var whiteCircle: Paint? = null

    private var wheelCenterX: Int = 0
    private var wheelCenterY: Int = 0
    private var whiteCircleRadius: Int = 0

    private var backgroundLeft: Int = 0
    private var backgroundTop: Int = 0
    private var backgroundRight: Int = 0
    private var backgroundBottom: Int = 0


    // Moving the wheel
    private val angle = 0f


    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initialize()
    }

    private fun initialize() {

        // Initialize the ViewElements
        whiteCircle = Paint()
        whiteCircle!!.flags = Paint.ANTI_ALIAS_FLAG
        whiteCircle!!.color = resources.getColor(R.color.white)

        backGround = Paint()
        backGround!!.flags = Paint.ANTI_ALIAS_FLAG
        backGround!!.color = resources.getColor(R.color.black)
        backGround!!.style = Paint.Style.FILL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        val newHeight = View.MeasureSpec.getSize(heightMeasureSpec)
        val newWidth = View.MeasureSpec.getSize(widthMeasureSpec)
        setupDimensions(newWidth, newHeight)

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }


    override fun onDraw(canvas: Canvas) {
        Timber.d("onDraw(): angle: %f", angle)

        canvas.drawRect(
                backgroundLeft.toFloat(), backgroundTop.toFloat(),
                backgroundRight.toFloat(), backgroundBottom.toFloat(),
                backGround!!)
        canvas.drawCircle(
                wheelCenterX.toFloat(), wheelCenterY.toFloat(),
                whiteCircleRadius.toFloat(),
                whiteCircle!!)
    }

    private fun setupDimensions(parentWidth: Int, parentHeight: Int) {
        Timber.v("setupDimensions(): %d x %d", parentWidth, parentHeight)

        val borderOffset = resources.getDimension(R.dimen.jog_wheel_border)
        whiteCircleRadius = parentHeight / 2

        wheelCenterX = (parentWidth / 2
                + (parentWidth / 2 - parentHeight / 2)
                + borderOffset.toInt() / 2)

        wheelCenterY = whiteCircleRadius

        backgroundLeft = wheelCenterX
        backgroundRight = parentWidth
        backgroundTop = 0
        backgroundBottom = parentHeight
    }
}
