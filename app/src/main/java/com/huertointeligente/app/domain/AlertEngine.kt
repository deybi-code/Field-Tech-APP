package com.huertointeligente.app.domain

import android.content.Context
import com.huertointeligente.app.R
import com.huertointeligente.app.data.model.AlertItem
import com.huertointeligente.app.data.model.AlertType
import com.huertointeligente.app.data.model.Huerto
import com.huertointeligente.app.data.model.SensorReading
import com.huertointeligente.app.data.model.Severity

/** Convierte una lectura en alertas (sin guardar nada ni repetir avisos). */
object AlertEngine {

    fun evaluate(ctx: Context, h: Huerto, r: SensorReading, crop: Crop): List<AlertItem> {
        val now = System.currentTimeMillis()
        val out = mutableListOf<AlertItem>()

        fun add(type: AlertType, sev: Severity, title: String, message: String) {
            out += AlertItem(
                huertoId = h.id, timestamp = now, type = type,
                severity = sev, title = title, message = message
            )
        }

        // Humedad del suelo
        val soil = r.soilMoisture
        if (soil < crop.soil.min) {
            val critical = soil < crop.soil.min - 15f
            add(
                AlertType.SOIL_DRY,
                if (critical) Severity.CRITICAL else Severity.WARNING,
                ctx.getString(
                    if (critical) R.string.alert_soil_dry_critical_title else R.string.alert_soil_dry_warn_title
                ),
                ctx.getString(R.string.alert_soil_dry_msg, soil, crop.soil.min)
            )
        } else if (soil > crop.soil.max + 10f) {
            add(
                AlertType.SOIL_WET, Severity.WARNING,
                ctx.getString(R.string.alert_soil_wet_title),
                ctx.getString(R.string.alert_soil_wet_msg, soil, crop.soil.max)
            )
        }

        // Temperatura ambiente
        val temp = r.airTemp
        if (temp > crop.airTemp.max) {
            add(
                AlertType.HEAT,
                if (temp > crop.airTemp.max + 4f) Severity.CRITICAL else Severity.WARNING,
                ctx.getString(R.string.alert_heat_title),
                ctx.getString(R.string.alert_heat_msg, temp, crop.airTemp.max)
            )
        } else if (temp < crop.airTemp.min - 3f) {
            add(
                AlertType.COLD,
                if (temp < 5f) Severity.CRITICAL else Severity.WARNING,
                ctx.getString(R.string.alert_cold_title),
                ctx.getString(R.string.alert_cold_msg, temp, crop.airTemp.min)
            )
        }

        // Batería del dispositivo
        if (r.battery in 0..19) {
            add(
                AlertType.LOW_BATTERY,
                if (r.battery < 10) Severity.CRITICAL else Severity.WARNING,
                ctx.getString(R.string.alert_battery_title),
                ctx.getString(R.string.alert_battery_msg, r.battery)
            )
        }
        return out
    }

    fun offline(ctx: Context, h: Huerto): AlertItem = AlertItem(
        huertoId = h.id,
        timestamp = System.currentTimeMillis(),
        type = AlertType.OFFLINE,
        severity = Severity.WARNING,
        title = ctx.getString(R.string.alert_offline_title),
        message = ctx.getString(R.string.alert_offline_msg, h.name)
    )
}
