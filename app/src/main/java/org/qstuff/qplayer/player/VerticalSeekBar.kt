package org.qstuff.qplayer.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatSeekBar

import org.qstuff.qplayer.R

/**
 * Thanks to http://kersevanivan.org
 *
 * @author claus chierici (cc@codeyard.de)
 */
class VerticalSeekBar : AppCompatSeekBar {

    private lateinit var rect: Rect
    private lateinit var paint: Paint
    private var seekbarWidth = 0

    private var seekbarListener: SeekBar.OnSeekBarChangeListener? = null

    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {}

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {

        rect = Rect()
        paint = Paint()
        val r = resources
        seekbarWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                r.getDimension(R.dimen.pitchbar_width),
                r.displayMetrics).toInt()
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(h, w, oldh, oldw)
    }


    @Synchronized
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(measuredHeight, measuredWidth)
    }

    override fun setOnSeekBarChangeListener(mListener: SeekBar.OnSeekBarChangeListener) {
        this.seekbarListener = mListener
    }

    fun set(progress: Int) {

        setProgress(progress)
        onSizeChanged(width, height, 0, 0)
        if (seekbarListener != null)
            seekbarListener!!.onProgressChanged(this, progress, true)
    }

    fun reset() {

        progress = 500
        onSizeChanged(width, height, 0, 0)
        if (seekbarListener != null)
            seekbarListener!!.onProgressChanged(this, 500, true)
    }

    /**
     * this does the rotation
     */
    override fun onDraw(canvas: Canvas) {

        canvas.rotate(-90f)
        canvas.translate((-height).toFloat(), 0f)
        val offset = thumbOffset

        val progress = progress

        rect.set(offset,
                width / 2 - seekbarWidth / 2,
                height,
                width / 2 + seekbarWidth / 2)

        paint.color = resources.getColor(R.color.black)

        canvas.drawRect(rect, paint)

        val diff = height.toFloat() / 1000

        if (progress > 500) {

            rect.set(height / 2,
                    width / 2 - seekbarWidth / 2,
                    (height / 2 + diff * (progress - 500)).toInt(),
                    width / 2 + seekbarWidth / 2)

            paint.color = resources.getColor(R.color.q_orange)
            canvas.drawRect(rect, paint)
        }

        if (progress < 500) {

            rect.set((height / 2 - diff * (500 - progress)).toInt(),
                    width / 2 - seekbarWidth / 2,
                    height / 2,
                    width / 2 + seekbarWidth / 2)

            paint.color = resources.getColor(R.color.q_orange)
            canvas.drawRect(rect, paint)
        }

        super.onDraw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        if (!isEnabled || seekbarListener == null) {
            return false
        }

        when (event.action) {

            MotionEvent.ACTION_DOWN -> if (seekbarListener != null)
                seekbarListener!!.onStartTrackingTouch(this)

            MotionEvent.ACTION_MOVE -> {

                var position = max - (max * event.y / height).toInt()

                if (position < 0)
                    position = 0

                if (position > max)
                    position = max

                progress = position
                onSizeChanged(width, height, 0, 0)

                if (seekbarListener != null) {
                    seekbarListener!!.onProgressChanged(this, position, true)
                }
            }

            MotionEvent.ACTION_UP ->

                if (seekbarListener != null) {
                    seekbarListener!!.onStopTrackingTouch(this)
                }

            MotionEvent.ACTION_CANCEL -> {
            }
        }

        return true
    }
}
