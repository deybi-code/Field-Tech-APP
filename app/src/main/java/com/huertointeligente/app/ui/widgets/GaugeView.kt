package com.huertointeligente.app.ui.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import com.huertointeligente.app.util.themeColor
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Medidor circular (0–100 %) con zona ideal marcada. */
class GaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var shown = 0f
    private var idealMin = -1f
    private var idealMax = -1f
    private var animator: ValueAnimator? = null

    var arcColor: Int = Color.parseColor("#2E7D32")
        set(value) {
            field = value
            invalidate()
        }

    var caption: String = ""
        set(value) {
            field = value
            invalidate()
        }

    private val stroke = dp(16f)
    private val rect = RectF()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        color = ColorUtils.setAlphaComponent(
            context.themeColor(com.google.android.material.R.attr.colorOnSurface, Color.GRAY), 28
        )
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    private val idealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(5f)
        strokeCap = Paint.Cap.BUTT
        color = ColorUtils.setAlphaComponent(
            context.themeColor(com.google.android.material.R.attr.colorSecondary, Color.parseColor("#002B49")), 80
        )
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(44f)
        isFakeBoldText = true
        color = context.themeColor(com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(13f)
        color = context.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
    }

    /** Muestra la zona ideal como un arco fino en el interior del medidor. */
    fun setIdeal(min: Float, max: Float) {
        idealMin = min
        idealMax = max
        invalidate()
    }

    fun setValue(v: Float, animate: Boolean = true) {
        val target = v.coerceIn(0f, 100f)
        animator?.cancel()
        contentDescription = "${target.roundToInt()} %"
        if (!animate || abs(target - shown) < 0.5f) {
            shown = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(shown, target).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                shown = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f + dp(8f)
        val radius = min(width, height) / 2f - stroke
        if (radius <= 0f) return

        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(rect, START_ANGLE, SWEEP, false, trackPaint)

        if (idealMin >= 0f && idealMax > idealMin) {
            val inner = radius - stroke
            rect.set(cx - inner, cy - inner, cx + inner, cy + inner)
            canvas.drawArc(
                rect,
                START_ANGLE + SWEEP * idealMin / 100f,
                SWEEP * (idealMax - idealMin) / 100f,
                false, idealPaint
            )
            rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        }

        arcPaint.color = arcColor
        if (shown > 0.2f) {
            canvas.drawArc(rect, START_ANGLE, SWEEP * shown / 100f, false, arcPaint)
        }

        canvas.drawText("${shown.roundToInt()}%", cx, cy + dp(6f), valuePaint)
        if (caption.isNotEmpty()) {
            canvas.drawText(caption, cx, cy + dp(32f), captionPaint)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun sp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private companion object {
        const val START_ANGLE = 135f
        const val SWEEP = 270f
    }
}
