package com.huertointeligente.app.data

import android.content.Context
import com.huertointeligente.app.data.local.AppDatabase
import com.huertointeligente.app.data.local.Prefs
import com.huertointeligente.app.data.model.AlertItem
import com.huertointeligente.app.data.model.DataSourceMode
import com.huertointeligente.app.data.model.Huerto
import com.huertointeligente.app.data.model.IrrigationLog
import com.huertointeligente.app.data.model.SensorReading
import com.huertointeligente.app.data.remote.HttpDataSource
import com.huertointeligente.app.data.remote.SensorDataSource
import com.huertointeligente.app.data.remote.SimulatedDataSource
import com.huertointeligente.app.domain.AlertEngine
import com.huertointeligente.app.domain.Crops
import com.huertointeligente.app.notifications.Notifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Punto único de acceso a los datos: decide la fuente (simulada o dispositivo real),
 * guarda el historial, evalúa alertas y dispara notificaciones.
 */
class Repository(
    private val context: Context,
    private val db: AppDatabase,
    private val prefs: Prefs
) {
    private val httpSource = HttpDataSource()

    private fun source(): SensorDataSource =
        if (prefs.mode == DataSourceMode.SIMULATED) SimulatedDataSource else httpSource

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    // ------------------------------------------------------------------ Huertos

    suspend fun huertos(): List<Huerto> = io { db.getHuertos() }

    suspend fun saveHuerto(h: Huerto): Long = io {
        val id = if (h.id == 0L) db.insertHuerto(h) else {
            db.updateHuerto(h)
            h.id
        }
        if (prefs.mode == DataSourceMode.SIMULATED && db.countReadings(id) == 0) seedHistory(id)
        id
    }

    suspend fun deleteHuerto(id: Long) = io { db.deleteHuerto(id) }

    // ----------------------------------------------------------------- Lecturas

    suspend fun latest(huertoId: Long): SensorReading? = io { db.latestReading(huertoId) }

    /** Historial reducido a un máximo de puntos (promedia por intervalos) para graficar. */
    suspend fun history(huertoId: Long, sinceMs: Long, maxPoints: Int = 240): List<SensorReading> = io {
        val now = System.currentTimeMillis()
        downsample(db.readingsSince(huertoId, sinceMs), sinceMs, now, maxPoints)
    }

    /**
     * Lee el sensor, guarda la lectura (como máximo una cada 5 min) y evalúa alertas.
     * Devuelve la lectura o el error de conexión.
     */
    suspend fun refresh(h: Huerto): Result<SensorReading> = withContext(Dispatchers.IO) {
        try {
            val r = source().read(h)
            val last = db.latestReading(h.id)
            if (last == null || r.timestamp - last.timestamp >= STORE_INTERVAL_MS) {
                db.insertReading(r)
            }
            evaluate(h, r)
            Result.success(r)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            registerFailure(h)
            Result.failure(e)
        }
    }

    /** Prueba una URL sin guardar nada (botón "Probar conexión"). */
    suspend fun testUrl(url: String): Result<SensorReading> = try {
        Result.success(httpSource.fetch(url, 0L))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ------------------------------------------------------------------ Alertas

    private fun evaluate(h: Huerto, r: SensorReading) {
        val candidates = AlertEngine.evaluate(context, h, r, Crops.forHuerto(h))
        storeNewAlerts(h, candidates, ALERT_COOLDOWN_MS)
    }

    private fun registerFailure(h: Huerto) {
        if (prefs.mode != DataSourceMode.HTTP) return
        val last = db.latestReading(h.id)
        val stale = last == null || System.currentTimeMillis() - last.timestamp > OFFLINE_AFTER_MS
        if (stale) storeNewAlerts(h, listOf(AlertEngine.offline(context, h)), OFFLINE_COOLDOWN_MS)
    }

    private fun storeNewAlerts(h: Huerto, candidates: List<AlertItem>, cooldownMs: Long) {
        val now = System.currentTimeMillis()
        for (c in candidates) {
            val lastTs = db.lastAlertTime(h.id, c.type)
            if (lastTs != null && now - lastTs < cooldownMs) continue
            val id = db.insertAlert(c)
            if (prefs.notificationsEnabled) Notifier.show(context, h, c.copy(id = id))
        }
    }

    suspend fun alerts(): List<AlertItem> = io { db.getAlerts() }
    suspend fun unreadCount(): Int = io { db.unreadAlertCount() }
    suspend fun markAlertsRead() = io { db.markAllAlertsRead() }
    suspend fun clearAlerts() = io { db.clearAlerts() }

    // ------------------------------------------------------------------- Riegos

    suspend fun irrigations(huertoId: Long): List<IrrigationLog> = io { db.getIrrigations(huertoId) }

    suspend fun addIrrigation(huertoId: Long, minutes: Int, note: String) = io {
        db.insertIrrigation(
            IrrigationLog(
                huertoId = huertoId,
                timestamp = System.currentTimeMillis(),
                minutes = minutes,
                note = note
            )
        )
    }

    // ------------------------------------------------------- Datos de demostración

    /** Crea 7 días de historial simulado para los huertos que no tengan lecturas. */
    suspend fun ensureDemoData() {
        withContext(Dispatchers.IO) {
            if (prefs.mode == DataSourceMode.SIMULATED) {
                seedMissingHistory()
            }
        }
    }

    suspend fun onModeChanged() {
        withContext(Dispatchers.IO) {
            if (prefs.mode == DataSourceMode.HTTP) {
                db.deleteSimulatedReadings()
            } else {
                seedMissingHistory()
            }
        }
    }

    suspend fun regenerateDemo() {
        withContext(Dispatchers.IO) {
            db.deleteSimulatedReadings()
            if (prefs.mode == DataSourceMode.SIMULATED) {
                seedMissingHistory()
            }
        }
    }

    private fun seedMissingHistory() {
        for (h in db.getHuertos()) {
            if (db.countReadings(h.id) == 0) seedHistory(h.id)
        }
    }

    suspend fun clearHistory() = io { db.deleteAllReadings() }

    suspend fun pruneOld() = io {
        db.deleteReadingsOlderThan(System.currentTimeMillis() - RETENTION_MS)
    }

    private fun seedHistory(huertoId: Long) {
        val now = System.currentTimeMillis()
        val step = 15 * 60 * 1000L
        val count = (7 * 24 * 60 * 60 * 1000L / step).toInt()
        val list = ArrayList<SensorReading>(count)
        for (i in count downTo 1) {
            list.add(SimulatedDataSource.generate(huertoId, now - i * step))
        }
        db.insertReadings(list)
    }

    private fun downsample(
        src: List<SensorReading>,
        from: Long,
        to: Long,
        maxPoints: Int
    ): List<SensorReading> {
        if (src.size <= maxPoints) return src
        val span = (to - from).coerceAtLeast(1L)
        val buckets = HashMap<Int, MutableList<SensorReading>>()
        for (r in src) {
            val idx = (((r.timestamp - from) * maxPoints) / span).toInt().coerceIn(0, maxPoints - 1)
            buckets.getOrPut(idx) { mutableListOf() }.add(r)
        }
        return buckets.toSortedMap().values.map { g ->
            SensorReading(
                huertoId = g[0].huertoId,
                timestamp = g.map { it.timestamp }.average().toLong(),
                soilMoisture = g.map { it.soilMoisture }.average().toFloat(),
                soilTemp = g.map { it.soilTemp }.average().toFloat(),
                airTemp = g.map { it.airTemp }.average().toFloat(),
                airHumidity = g.map { it.airHumidity }.average().toFloat(),
                battery = g.map { it.battery }.average().toInt(),
                rssi = g.map { it.rssi }.average().toInt(),
                simulated = g[0].simulated
            )
        }
    }

    private companion object {
        const val STORE_INTERVAL_MS = 5 * 60 * 1000L
        const val ALERT_COOLDOWN_MS = 3 * 60 * 60 * 1000L
        const val OFFLINE_COOLDOWN_MS = 6 * 60 * 60 * 1000L
        const val OFFLINE_AFTER_MS = 15 * 60 * 1000L
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000L
    }
}
