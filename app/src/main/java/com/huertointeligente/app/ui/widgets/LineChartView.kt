package com.huertointeligente.app.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import com.huertointeligente.app.util.themeColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class ChartPoint(val t: Long, val v: Float)

/**
 * Gráfico de líneas propio (sin librerías): banda de rango ideal, cuadrícula,
 * relleno degradado y lectura al tocar/arrastrar.
 */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var points: List<ChartPoint> = emptyList()
    private var unit = ""
    private var lineColor = Color.parseColor("#2E7D32")
    private var idealMin: Float? = null
    private var idealMax: Float? = null
    private var selectedIndex = -1

    private val onSurface = context.themeColor(com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
    private val onSurfaceVariant =
        context.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
    private val surface = context.themeColor(com.google.android.material.R.attr.colorSurface, Color.WHITE)

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = ColorUtils.setAlphaComponent(onSurface, 25)
    }
    private val axisText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(11f)
        color = onSurfaceVariant
    }
    private val emptyText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(14f)
        textAlign = Paint.Align.CENTER
        color = onSurfaceVariant
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ColorUtils.setAlphaComponent(
            context.themeColor(com.google.android.material.R.attr.colorSecondary, Color.parseColor("#002B49")), 40
        )
    }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = ColorUtils.setAlphaComponent(onSurface, 90)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = surface
    }
    private val tipBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tipText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(12f)
        color = Color.WHITE
        isFakeBoldText = true
    }

    private val path = Path()
    private val fillPath = Path()
    private val tipRect = RectF()

    fun setData(
        data: List<ChartPoint>,
        unit: String,
        color: Int,
        idealMin: Float? = null,
        idealMax: Float? = null
    ) {
        this.points = data
        this.unit = unit
        this.lineColor = color
        this.idealMin = idealMin
        this.idealMax = idealMax
        this.selectedIndex = -1
        invalidate()
    }

    private val plotLeft get() = dp(40f)
    private val plotRight get() = width - dp(10f)
    private val plotTop get() = dp(12f)
    private val plotBottom get() = height - dp(24f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pts = points
        if (pts.size < 2 || width == 0 || height == 0) return

        var minV = pts.minOf { it.v }
        var maxV = pts.maxOf { it.v }
        idealMin?.let { minV = min(minV, it) }
        idealMax?.let { maxV = max(maxV, it) }
        if (maxV - minV < 1f) {
            maxV += 0.5f
            minV -= 0.5f
        }
        val pad = (maxV - minV) * 0.1f
        minV -= pad
        maxV += pad

        val t0 = pts.first().t
        val t1 = pts.last().t
        val span = (t1 - t0).coerceAtLeast(1L)
        val l = plotLeft
        val r = plotRight
        val tp = plotTop
        val bt = plotBottom

        fun px(t: Long) = l + (t - t0).toFloat() / span * (r - l)
        fun py(v: Float) = bt - (v - minV) / (maxV - minV) * (bt - tp)

        // Banda del rango ideal
        val iMin = idealMin
        val iMax = idealMax
        if (iMin != null && iMax != null) {
            canvas.drawRect(l, py(iMax).coerceIn(tp, bt), r, py(iMin).coerceIn(tp, bt), bandPaint)
        }

        // Cuadrícula y etiquetas del eje Y
        axisText.textAlign = Paint.Align.RIGHT
        for (i in 0..3) {
            val v = minV + (maxV - minV) * i / 3f
            val y = py(v)
            canvas.drawLine(l, y, r, y, gridPaint)
            canvas.drawText(String.format(Locale.getDefault(), "%.0f", v), l - dp(6f), y + dp(4f), axisText)
        }

        // Etiquetas del eje X
        val fmt = SimpleDateFormat(if (span <= 26L * 3600_000L) "HH:mm" else "EEE d", Locale.getDefault())
        for (i in 0..3) {
            val t = t0 + span * i / 3
            axisText.textAlign = when (i) {
                0 -> Paint.Align.LEFT
                3 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            val x = when (i) {
                0 -> l
                3 -> r
                else -> px(t)
            }
            canvas.drawText(fmt.format(Date(t)), x, height - dp(6f), axisText)
        }

        // Línea y relleno
        path.reset()
        pts.forEachIndexed { i, p ->
            val x = px(p.t)
            val y = py(p.v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        fillPath.set(path)
        fillPath.lineTo(px(pts.last().t), bt)
        fillPath.lineTo(px(pts.first().t), bt)
        fillPath.close()
        fillPaint.shader = LinearGradient(
            0f, tp, 0f, bt,
            ColorUtils.setAlphaComponent(lineColor, 90),
            ColorUtils.setAlphaComponent(lineColor, 0),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)
        linePaint.color = lineColor
        canvas.drawPath(path, linePaint)

        // Punto seleccionado + etiqueta
        if (selectedIndex in pts.indices) {
            val p = pts[selectedIndex]
            val x = px(p.t)
            val y = py(p.v)
            canvas.drawLine(x, tp, x, bt, cursorPaint)
            dotPaint.color = lineColor
            canvas.drawCircle(x, y, dp(5f), dotPaint)
            canvas.drawCircle(x, y, dp(5f), dotRing)

            val timeFmt = SimpleDateFormat(
                if (span <= 26L * 3600_000L) "HH:mm" else "EEE d, HH:mm", Locale.getDefault()
            )
            val label = String.format(Locale.getDefault(), "%.1f %s · %s", p.v, unit, timeFmt.format(Date(p.t)))
            val tw = tipText.measureText(label)
            val padH = dp(8f)
            val tipW = tw + padH * 2
            val tipH = dp(24f)
            var tx = x - tipW / 2f
            tx = tx.coerceIn(l, max(l, r - tipW))
            val ty = max(tp - dp(4f), y - tipH - dp(10f))
            tipBg.color = ColorUtils.setAlphaComponent(Color.BLACK, 200)
            tipRect.set(tx, ty, tx + tipW, ty + tipH)
            canvas.drawRoundRect(tipRect, dp(8f), dp(8f), tipBg)
            canvas.drawText(label, tx + padH, ty + tipH / 2f + dp(4f), tipText)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (points.size < 2) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                selectedIndex = nearest(event.x)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun nearest(x: Float): Int {
        val t0 = points.first().t
        val span = (points.last().t - t0).coerceAtLeast(1L)
        var best = 0
        var bestDist = Float.MAX_VALUE
        for (i in points.indices) {
            val px = plotLeft + (points[i].t - t0).toFloat() / span * (plotRight - plotLeft)
            val d = abs(px - x)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun sp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
