package com.huertointeligente.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import com.huertointeligente.app.data.model.AlertItem
import com.huertointeligente.app.data.model.AlertType
import com.huertointeligente.app.data.model.Huerto
import com.huertointeligente.app.data.model.IrrigationLog
import com.huertointeligente.app.data.model.SensorReading
import com.huertointeligente.app.data.model.Severity

/**
 * Base de datos SQLite local (sin librerías externas).
 * Todas las funciones son bloqueantes: llámalas desde Dispatchers.IO.
 */
class AppDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE huertos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                location TEXT NOT NULL DEFAULT '',
                crop_id TEXT,
                area_ha REAL NOT NULL DEFAULT 0,
                device_url TEXT NOT NULL DEFAULT ''
            )"""
        )
        db.execSQL(
            """CREATE TABLE readings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                huerto_id INTEGER NOT NULL,
                ts INTEGER NOT NULL,
                soil_moisture REAL NOT NULL,
                soil_temp REAL NOT NULL,
                air_temp REAL NOT NULL,
                air_humidity REAL NOT NULL,
                battery INTEGER NOT NULL DEFAULT -1,
                rssi INTEGER NOT NULL DEFAULT 0,
                simulated INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL("CREATE INDEX idx_readings_huerto_ts ON readings(huerto_id, ts)")
        db.execSQL(
            """CREATE TABLE alerts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                huerto_id INTEGER NOT NULL,
                ts INTEGER NOT NULL,
                type TEXT NOT NULL,
                severity TEXT NOT NULL,
                title TEXT NOT NULL,
                message TEXT NOT NULL,
                is_read INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL("CREATE INDEX idx_alerts_ts ON alerts(ts)")
        db.execSQL(
            """CREATE TABLE irrigations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                huerto_id INTEGER NOT NULL,
                ts INTEGER NOT NULL,
                minutes INTEGER NOT NULL,
                note TEXT NOT NULL DEFAULT ''
            )"""
        )

        // Huertos de ejemplo (zonas de Virú y Chao, La Libertad)
        seedHuerto(db, "Huerto Virú · Lote 1", "Virú, La Libertad", "asparagus", 2.5)
        seedHuerto(db, "Huerto Chao · Lote A", "Chao, La Libertad", "blueberry", 1.2)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS irrigations")
        db.execSQL("DROP TABLE IF EXISTS alerts")
        db.execSQL("DROP TABLE IF EXISTS readings")
        db.execSQL("DROP TABLE IF EXISTS huertos")
        onCreate(db)
    }

    private fun seedHuerto(db: SQLiteDatabase, name: String, location: String, crop: String, area: Double) {
        val v = ContentValues().apply {
            put("name", name)
            put("location", location)
            put("crop_id", crop)
            put("area_ha", area)
            put("device_url", "")
        }
        db.insert("huertos", null, v)
    }

    // ------------------------------------------------------------------ Huertos

    fun getHuertos(): List<Huerto> =
        readableDatabase.rawQuery("SELECT * FROM huertos ORDER BY id", null).use { c ->
            buildList { while (c.moveToNext()) add(c.toHuerto()) }
        }

    fun insertHuerto(h: Huerto): Long = writableDatabase.insertOrThrow("huertos", null, h.toValues())

    fun updateHuerto(h: Huerto) {
        writableDatabase.update("huertos", h.toValues(), "id=?", arrayOf(h.id.toString()))
    }

    fun deleteHuerto(id: Long) {
        val args = arrayOf(id.toString())
        writableDatabase.transaction {
            delete("readings", "huerto_id=?", args)
            delete("alerts", "huerto_id=?", args)
            delete("irrigations", "huerto_id=?", args)
            delete("huertos", "id=?", args)
        }
    }

    // ----------------------------------------------------------------- Lecturas

    fun insertReading(r: SensorReading): Long = writableDatabase.insert("readings", null, r.toValues())

    fun insertReadings(list: List<SensorReading>) {
        writableDatabase.transaction {
            for (r in list) insert("readings", null, r.toValues())
        }
    }

    fun readingsSince(huertoId: Long, since: Long): List<SensorReading> =
        readableDatabase.rawQuery(
            "SELECT * FROM readings WHERE huerto_id=? AND ts>=? ORDER BY ts",
            arrayOf(huertoId.toString(), since.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(c.toReading()) } }

    fun latestReading(huertoId: Long): SensorReading? =
        readableDatabase.rawQuery(
            "SELECT * FROM readings WHERE huerto_id=? ORDER BY ts DESC LIMIT 1",
            arrayOf(huertoId.toString())
        ).use { c -> if (c.moveToFirst()) c.toReading() else null }

    fun countReadings(huertoId: Long): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM readings WHERE huerto_id=?",
            arrayOf(huertoId.toString())
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun deleteSimulatedReadings() {
        writableDatabase.delete("readings", "simulated=1", null)
    }

    fun deleteAllReadings() {
        writableDatabase.delete("readings", null, null)
    }

    fun deleteReadingsOlderThan(ts: Long) {
        writableDatabase.delete("readings", "ts<?", arrayOf(ts.toString()))
    }

    // ------------------------------------------------------------------ Alertas

    fun insertAlert(a: AlertItem): Long {
        val v = ContentValues().apply {
            put("huerto_id", a.huertoId)
            put("ts", a.timestamp)
            put("type", a.type.name)
            put("severity", a.severity.name)
            put("title", a.title)
            put("message", a.message)
            put("is_read", if (a.read) 1 else 0)
        }
        return writableDatabase.insert("alerts", null, v)
    }

    fun getAlerts(limit: Int = 200): List<AlertItem> =
        readableDatabase.rawQuery(
            "SELECT * FROM alerts ORDER BY ts DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(c.toAlert()) } }

    fun lastAlertTime(huertoId: Long, type: AlertType): Long? =
        readableDatabase.rawQuery(
            "SELECT MAX(ts) FROM alerts WHERE huerto_id=? AND type=?",
            arrayOf(huertoId.toString(), type.name)
        ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }

    fun unreadAlertCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM alerts WHERE is_read=0", null)
            .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun markAllAlertsRead() {
        val v = ContentValues().apply { put("is_read", 1) }
        writableDatabase.update("alerts", v, null, null)
    }

    fun clearAlerts() {
        writableDatabase.delete("alerts", null, null)
    }

    // ------------------------------------------------------------------- Riegos

    fun insertIrrigation(log: IrrigationLog): Long {
        val v = ContentValues().apply {
            put("huerto_id", log.huertoId)
            put("ts", log.timestamp)
            put("minutes", log.minutes)
            put("note", log.note)
        }
        return writableDatabase.insert("irrigations", null, v)
    }

    fun getIrrigations(huertoId: Long, limit: Int = 30): List<IrrigationLog> =
        readableDatabase.rawQuery(
            "SELECT * FROM irrigations WHERE huerto_id=? ORDER BY ts DESC LIMIT ?",
            arrayOf(huertoId.toString(), limit.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(c.toIrrigation()) } }

    // ------------------------------------------------------------ Conversiones

    private fun Huerto.toValues() = ContentValues().apply {
        put("name", name)
        put("location", location)
        put("crop_id", cropId)
        put("area_ha", areaHa)
        put("device_url", deviceUrl)
    }

    private fun SensorReading.toValues() = ContentValues().apply {
        put("huerto_id", huertoId)
        put("ts", timestamp)
        put("soil_moisture", soilMoisture.toDouble())
        put("soil_temp", soilTemp.toDouble())
        put("air_temp", airTemp.toDouble())
        put("air_humidity", airHumidity.toDouble())
        put("battery", battery)
        put("rssi", rssi)
        put("simulated", if (simulated) 1 else 0)
    }

    private fun Cursor.col(name: String) = getColumnIndexOrThrow(name)

    private fun Cursor.toHuerto() = Huerto(
        id = getLong(col("id")),
        name = getString(col("name")) ?: "",
        location = getString(col("location")) ?: "",
        cropId = if (isNull(col("crop_id"))) null else getString(col("crop_id")),
        areaHa = getDouble(col("area_ha")),
        deviceUrl = getString(col("device_url")) ?: ""
    )

    private fun Cursor.toReading() = SensorReading(
        id = getLong(col("id")),
        huertoId = getLong(col("huerto_id")),
        timestamp = getLong(col("ts")),
        soilMoisture = getFloat(col("soil_moisture")),
        soilTemp = getFloat(col("soil_temp")),
        airTemp = getFloat(col("air_temp")),
        airHumidity = getFloat(col("air_humidity")),
        battery = getInt(col("battery")),
        rssi = getInt(col("rssi")),
        simulated = getInt(col("simulated")) == 1
    )

    private fun Cursor.toAlert() = AlertItem(
        id = getLong(col("id")),
        huertoId = getLong(col("huerto_id")),
        timestamp = getLong(col("ts")),
        type = runCatching { AlertType.valueOf(getString(col("type"))) }.getOrDefault(AlertType.OFFLINE),
        severity = runCatching { Severity.valueOf(getString(col("severity"))) }.getOrDefault(Severity.INFO),
        title = getString(col("title")) ?: "",
        message = getString(col("message")) ?: "",
        read = getInt(col("is_read")) == 1
    )

    private fun Cursor.toIrrigation() = IrrigationLog(
        id = getLong(col("id")),
        huertoId = getLong(col("huerto_id")),
        timestamp = getLong(col("ts")),
        minutes = getInt(col("minutes")),
        note = getString(col("note")) ?: ""
    )

    private companion object {
        const val DB_NAME = "huerto_inteligente.db"
        const val DB_VERSION = 1
    }
}
