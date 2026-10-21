package com.huertointeligente.app.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.ColorUtils
import com.huertointeligente.app.util.themeColor

/** Barra horizontal 0–100 que muestra el rango ideal y un marcador con el valor actual. */
class RangeBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var value = -1f
    private var idealMin = 0f
    private var idealMax = 0f

    var markerColor: Int = Color.parseColor("#2E7D32")
        set(v) {
            field = v
            invalidate()
        }

    private val rect = RectF()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(
            context.themeColor(com.google.android.material.R.attr.colorOnSurface, Color.GRAY), 30
        )
    }
    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(
            context.themeColor(com.google.android.material.R.attr.colorPrimary, Color.parseColor("#00A3C4")), 100
        )
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        color = context.themeColor(com.google.android.material.R.attr.colorSurface, Color.WHITE)
    }

    fun set(value: Float, idealMin: Float, idealMax: Float) {
        this.value = value.coerceIn(0f, 100f)
        this.idealMin = idealMin.coerceIn(0f, 100f)
        this.idealMax = idealMax.coerceIn(0f, 100f)
        invalidate()
    }

    fun clear() {
        value = -1f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = dp(12f)
        val left = pad
        val right = width - pad
        val cy = height / 2f
        val half = dp(5f)
        val w = right - left
        if (w <= 0f) return

        rect.set(left, cy - half, right, cy + half)
        canvas.drawRoundRect(rect, half, half, trackPaint)

        if (idealMax > idealMin) {
            rect.set(left + w * idealMin / 100f, cy - half, left + w * idealMax / 100f, cy + half)
            canvas.drawRoundRect(rect, half, half, zonePaint)
        }
        if (value >= 0f) {
            val x = left + w * value / 100f
            markerPaint.color = markerColor
            canvas.drawCircle(x, cy, dp(9f), markerPaint)
            canvas.drawCircle(x, cy, dp(9f), ringPaint)
        }
    }

    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
}
