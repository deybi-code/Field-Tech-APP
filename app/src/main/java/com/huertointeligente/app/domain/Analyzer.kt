package com.huertointeligente.app.domain

import android.content.Context
import com.huertointeligente.app.R
import com.huertointeligente.app.data.model.SensorReading
import kotlin.math.exp
import kotlin.math.max

enum class Level { LOW, OPTIMAL, HIGH }

enum class AdviceType { IRRIGATE_NOW, IRRIGATE_SOON, NO_IRRIGATION, EXCESS_WATER, NO_DATA }

data class Advice(
    val type: AdviceType,
    val title: String,
    val detail: String,
    val tip: String,
    val intensity: String?
)

/** Lógica pura de análisis agronómico (sin dependencias de la interfaz). */
object Analyzer {

    val SOIL_TEMP_RANGE = Range(14f, 30f)
    val VPD_RANGE = Range(0.4f, 1.6f)

    fun level(v: Float, r: Range): Level = when {
        v < r.min -> Level.LOW
        v > r.max -> Level.HIGH
        else -> Level.OPTIMAL
    }

    /** Déficit de presión de vapor (kPa): indica qué tan "sediento" está el aire. */
    fun vpd(tempC: Float, humidityPct: Float): Float {
        val t = tempC.toDouble()
        val svp = 0.6108 * exp(17.27 * t / (t + 237.3))
        return (svp * (1.0 - humidityPct / 100.0)).toFloat().coerceAtLeast(0f)
    }

    fun levelLabel(ctx: Context, l: Level): String = ctx.getString(
        when (l) {
            Level.LOW -> R.string.level_low
            Level.OPTIMAL -> R.string.level_optimal
            Level.HIGH -> R.string.level_high
        }
    )

    fun vpdLabel(ctx: Context, l: Level): String = ctx.getString(
        when (l) {
            Level.LOW -> R.string.vpd_low
            Level.OPTIMAL -> R.string.vpd_normal
            Level.HIGH -> R.string.vpd_high
        }
    )

    fun irrigationAdvice(ctx: Context, r: SensorReading?, crop: Crop): Advice {
        if (r == null) {
            return Advice(
                AdviceType.NO_DATA,
                ctx.getString(R.string.advice_nodata_title),
                ctx.getString(R.string.advice_nodata_detail),
                "", null
            )
        }
        val s = r.soilMoisture
        val range = crop.soil
        val cropName = ctx.getString(crop.nameRes)

        return when {
            s < range.min - 8f -> Advice(
                AdviceType.IRRIGATE_NOW,
                ctx.getString(R.string.advice_now_title),
                ctx.getString(R.string.advice_now_detail, s, cropName, range.min, range.max),
                tip(ctx, AdviceType.IRRIGATE_NOW, r, crop),
                intensity(ctx, range.mid - s)
            )
            s < range.min + 4f -> Advice(
                AdviceType.IRRIGATE_SOON,
                ctx.getString(R.string.advice_soon_title),
                ctx.getString(R.string.advice_soon_detail, s, cropName, range.min),
                tip(ctx, AdviceType.IRRIGATE_SOON, r, crop),
                intensity(ctx, range.mid - s)
            )
            s <= range.max -> Advice(
                AdviceType.NO_IRRIGATION,
                ctx.getString(R.string.advice_none_title),
                ctx.getString(R.string.advice_none_detail, s, cropName),
                ctx.getString(R.string.tip_none),
                null
            )
            else -> Advice(
                AdviceType.EXCESS_WATER,
                ctx.getString(R.string.advice_excess_title),
                ctx.getString(R.string.advice_excess_detail, s, cropName, range.max),
                ctx.getString(R.string.tip_excess),
                null
            )
        }
    }

    private fun intensity(ctx: Context, deficit: Float): String = ctx.getString(
        when {
            deficit < 10f -> R.string.intensity_light
            deficit < 20f -> R.string.intensity_moderate
            else -> R.string.intensity_deep
        }
    )

    private fun tip(ctx: Context, type: AdviceType, r: SensorReading, crop: Crop): String = when {
        type == AdviceType.EXCESS_WATER -> ctx.getString(R.string.tip_excess)
        r.airTemp >= crop.airTemp.max - 2f -> ctx.getString(R.string.tip_hot, r.airTemp)
        r.airTemp <= crop.airTemp.min + 2f -> ctx.getString(R.string.tip_cold, r.airTemp)
        else -> ctx.getString(R.string.tip_normal)
    }

    /** Qué tan cerca está el valor del rango ideal: 1 = dentro, 0 = muy alejado. */
    private fun closeness(v: Float, r: Range): Float {
        if (v in r) return 1f
        val width = max(r.max - r.min, 1f)
        val dist = if (v < r.min) r.min - v else v - r.max
        return max(0f, 1f - dist / width)
    }

    /** Compatibilidad 0..100 de un cultivo con las condiciones registradas. */
    fun compatibility(crop: Crop, readings: List<SensorReading>): Int {
        if (readings.isEmpty()) return 0
        var soil = 0f
        var air = 0f
        var hum = 0f
        for (r in readings) {
            soil += closeness(r.soilMoisture, crop.soil)
            air += closeness(r.airTemp, crop.airTemp)
            hum += closeness(r.airHumidity, crop.airHumidity)
        }
        val n = readings.size.toFloat()
        val score = (soil / n) * 0.35f + (air / n) * 0.40f + (hum / n) * 0.25f
        return (score * 100f).toInt().coerceIn(0, 100)
    }

    fun compatLabelRes(score: Int): Int = when {
        score >= 80 -> R.string.compat_very
        score >= 60 -> R.string.compat_good
        score >= 40 -> R.string.compat_fair
        else -> R.string.compat_poor
    }
}
