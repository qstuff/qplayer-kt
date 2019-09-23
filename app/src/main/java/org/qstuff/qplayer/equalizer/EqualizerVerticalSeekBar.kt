package org.qstuff.qplayer.equalizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatSeekBar
import org.qstuff.qplayer.R

/**
 * Thanks to http://kersevanivan.org
 *
 * @author claus chierici (cc@codeyard.de)
 */
class EqualizerVerticalSeekBar : AppCompatSeekBar {

    companion object {
        const val PROGRESS_MAX = 800
        const val PROGRESS_START = 300
    }

    private var progressRect: Rect = Rect()
    private var progressPaint: Paint = Paint()
    private var zeroRect: Rect = Rect()
    private var zeroPaint: Paint = Paint()

    private var seekbarWidth = 0

    private var seekbarListener: OnSeekBarChangeListener? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {

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

    override fun setOnSeekBarChangeListener(mListener: OnSeekBarChangeListener) {
        this.seekbarListener = mListener
    }

    fun setNewProgress(progress: Int, notifyListener: Boolean) {

        setProgress(progress)
        onSizeChanged(width, height, 0, 0)
        if (seekbarListener != null && notifyListener) {
            seekbarListener!!.onProgressChanged(this, progress, true)
        }
    }

    fun reset() {

        progress = PROGRESS_START
        onSizeChanged(width, height, 0, 0)
        if (seekbarListener != null) {
            seekbarListener!!.onProgressChanged(this, 500, true)
        }
    }

    /**
     * this does the rotation
     */
    override fun onDraw(canvas: Canvas) {

        canvas.rotate(-90f)
        canvas.translate((-height).toFloat(), 0f)
        val offset = thumbOffset

        val progress = progress

        canvas.drawRect(progressRect, progressPaint)

        val diff = height.toFloat() / PROGRESS_MAX

        progressRect.set(0,
                width / 2 - seekbarWidth / 2,
                (diff * (progress)).toInt(),
                width / 2 + seekbarWidth / 2)

        progressPaint.color = resources.getColor(R.color.q_orange)
        canvas.drawRect(progressRect, progressPaint)

        zeroRect.set(PROGRESS_START + offset,
                width ,
                    PROGRESS_START + offset,
                width )
        zeroPaint.color = resources.getColor(R.color.black)
        zeroPaint.strokeWidth = 2f
        canvas.drawLine(PROGRESS_START.toFloat(),
                0f ,
                PROGRESS_START.toFloat(),
                width.toFloat(), zeroPaint)

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
