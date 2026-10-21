package com.huertointeligente.app.data.remote

import com.huertointeligente.app.data.model.Huerto
import com.huertointeligente.app.data.model.SensorReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Origen de las lecturas de sensores. Lanza excepción si no se puede leer. */
interface SensorDataSource {
    suspend fun read(huerto: Huerto): SensorReading
}

/**
 * Lee el JSON del dispositivo (ESP32 por WiFi, o Firebase Realtime Database vía REST).
 *
 * Formato esperado (ver README):
 * {"soil_moisture":54.2,"soil_temp":22.1,"air_temp":24.3,"air_humidity":61.0,
 *  "battery":87,"rssi":-62,"timestamp":1758400000}
 */
class HttpDataSource : SensorDataSource {

    override suspend fun read(huerto: Huerto): SensorReading = fetch(huerto.deviceUrl, huerto.id)

    suspend fun fetch(url: String, huertoId: Long): SensorReading = withContext(Dispatchers.IO) {
        val target = url.trim()
        require(target.isNotEmpty()) { "El huerto no tiene URL de dispositivo" }
        val conn = URL(target).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5000
            conn.readTimeout = 6000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parse(body, huertoId)
        } finally {
            conn.disconnect()
        }
    }

    internal fun parse(body: String, huertoId: Long): SensorReading {
        val text = body.trim()
        if (text.isEmpty() || text == "null") error("El dispositivo no devolvió datos")
        val j = JSONObject(text)

        fun need(key: String): Float {
            if (!j.has(key) || j.isNull(key)) error("Falta el campo \"$key\" en la respuesta")
            return j.getDouble(key).toFloat()
        }

        val now = System.currentTimeMillis()
        var ts = j.optLong("timestamp", 0L)
        if (ts in 1 until 100_000_000_000L) ts *= 1000L          // segundos -> milisegundos
        if (ts < 1_500_000_000_000L) ts = now                    // sin hora válida (ej. sin NTP)

        return SensorReading(
            huertoId = huertoId,
            timestamp = ts,
            soilMoisture = need("soil_moisture").coerceIn(0f, 100f),
            soilTemp = need("soil_temp"),
            airTemp = need("air_temp"),
            airHumidity = need("air_humidity").coerceIn(0f, 100f),
            battery = j.optInt("battery", -1),
            rssi = j.optInt("rssi", 0)
        )
    }
}

/**
 * Genera datos realistas para el modo demostración. Es determinista:
 * el mismo (huerto, instante) produce siempre la misma lectura, así el historial
 * y las lecturas en vivo son coherentes entre sí.
 */
object SimulatedDataSource : SensorDataSource {

    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR

    override suspend fun read(huerto: Huerto): SensorReading =
        generate(huerto.id, System.currentTimeMillis())

    fun generate(huertoId: Long, t: Long): SensorReading {
        val id = huertoId.coerceAtLeast(0L)
        val variant = (id % 3L).toInt()

        val tzOffset = TimeZone.getDefault().getOffset(t)
        val msOfDay = ((t + tzOffset) % DAY + DAY) % DAY
        val localHour = msOfDay / HOUR.toDouble()

        // Temperatura del aire: mínimo ~06:00, máximo ~15:00
        val airTemp = 20.0 + variant * 0.9 +
            4.5 * sin(2 * PI * (localHour - 9.0) / 24.0) +
            noise(t / 300_000L + id) * 0.4

        // Humedad del aire: inversa a la temperatura
        val airHum = (86.0 - (airTemp - 16.0) * 2.6 + noise(t / 420_000L + id * 17) * 2.0)
            .coerceIn(35.0, 98.0)

        // Temperatura del suelo: más suave y con retraso respecto al aire
        val soilTemp = 21.0 + variant * 0.5 +
            3.2 * sin(2 * PI * (localHour - 12.0) / 24.0) +
            noise(t / 900_000L + id * 5) * 0.1

        // Humedad del suelo: se seca de forma gradual y "sube" al regar (diente de sierra)
        val period = (30 + variant * 6) * HOUR
        val phase = ((t + id * 7 * HOUR) % period).toDouble() / period.toDouble()
        val soil = 80.0 - 44.0 * phase.pow(0.85) + noise(t / 600_000L + id * 31) * 0.8

        // Batería: se descarga durante 6 días y luego se "recarga"
        val batteryPeriod = 6 * DAY
        val batPhase = ((t + id * 3 * HOUR) % batteryPeriod).toDouble() / batteryPeriod.toDouble()
        val battery = (100.0 - 85.0 * batPhase).roundToInt()

        val rssi = (-58.0 + noise(t / 900_000L + id * 3) * 7.0).roundToInt()

        return SensorReading(
            huertoId = id,
            timestamp = t,
            soilMoisture = soil.toFloat().coerceIn(0f, 100f),
            soilTemp = soilTemp.toFloat(),
            airTemp = airTemp.toFloat(),
            airHumidity = airHum.toFloat(),
            battery = battery,
            rssi = rssi,
            simulated = true
        )
    }

    /** Ruido pseudoaleatorio determinista en el rango [-1, 1]. */
    private fun noise(seed: Long): Double {
        val x = sin(seed * 12.9898) * 43758.5453
        return (x - floor(x)) * 2.0 - 1.0
    }
}
