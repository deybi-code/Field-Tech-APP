package com.huertointeligente.app.data.model

data class Huerto(
    val id: Long = 0L,
    val name: String,
    val location: String = "",
    val cropId: String? = null,
    val areaHa: Double = 0.0,
    val deviceUrl: String = ""
)

data class SensorReading(
    val id: Long = 0L,
    val huertoId: Long,
    val timestamp: Long,
    val soilMoisture: Float,
    val soilTemp: Float,
    val airTemp: Float,
    val airHumidity: Float,
    /** Porcentaje 0..100, o -1 si el dispositivo no lo reporta. */
    val battery: Int = -1,
    /** Intensidad de señal WiFi en dBm, o 0 si no se reporta. */
    val rssi: Int = 0,
    val simulated: Boolean = false
)

enum class Metric { SOIL_MOISTURE, SOIL_TEMP, AIR_TEMP, AIR_HUMIDITY }

fun SensorReading.metric(m: Metric): Float = when (m) {
    Metric.SOIL_MOISTURE -> soilMoisture
    Metric.SOIL_TEMP -> soilTemp
    Metric.AIR_TEMP -> airTemp
    Metric.AIR_HUMIDITY -> airHumidity
}

enum class AlertType { SOIL_DRY, SOIL_WET, HEAT, COLD, LOW_BATTERY, OFFLINE }

enum class Severity { INFO, WARNING, CRITICAL }

data class AlertItem(
    val id: Long = 0L,
    val huertoId: Long,
    val timestamp: Long,
    val type: AlertType,
    val severity: Severity,
    val title: String,
    val message: String,
    val read: Boolean = false
)

data class IrrigationLog(
    val id: Long = 0L,
    val huertoId: Long,
    val timestamp: Long,
    val minutes: Int,
    val note: String = ""
)

enum class DataSourceMode { SIMULATED, HTTP }
