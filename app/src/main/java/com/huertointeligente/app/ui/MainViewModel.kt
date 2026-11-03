package com.huertointeligente.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huertointeligente.app.App
import com.huertointeligente.app.data.model.AlertItem
import com.huertointeligente.app.data.model.Huerto
import com.huertointeligente.app.data.model.IrrigationLog
import com.huertointeligente.app.data.model.SensorReading
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ViewModel compartido por toda la app: huerto seleccionado, última lectura,
 * estado de conexión, alertas y consulta periódica (polling) mientras la app está visible.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface Conn {
        object Idle : Conn
        object Loading : Conn
        object Online : Conn
        data class Error(val message: String) : Conn
    }

    private val app = application as App
    private val repo = app.repository
    val prefs = app.prefs

    private val _huertos = MutableStateFlow<List<Huerto>>(emptyList())
    val huertos: StateFlow<List<Huerto>> = _huertos.asStateFlow()

    private val _selected = MutableStateFlow<Huerto?>(null)
    val selected: StateFlow<Huerto?> = _selected.asStateFlow()

    private val _reading = MutableStateFlow<SensorReading?>(null)
    val reading: StateFlow<SensorReading?> = _reading.asStateFlow()

    private val _conn = MutableStateFlow<Conn>(Conn.Idle)
    val conn: StateFlow<Conn> = _conn.asStateFlow()

    private val _alerts = MutableStateFlow<List<AlertItem>>(emptyList())
    val alerts: StateFlow<List<AlertItem>> = _alerts.asStateFlow()

    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    private val _irrigations = MutableStateFlow<List<IrrigationLog>>(emptyList())
    val irrigations: StateFlow<List<IrrigationLog>> = _irrigations.asStateFlow()

    private val _autoSwitchEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val autoSwitchEvent: SharedFlow<String> = _autoSwitchEvent.asSharedFlow()

    private val _lotPromptEvent = MutableSharedFlow<Huerto>(extraBufferCapacity = 1)
    val lotPromptEvent: SharedFlow<Huerto> = _lotPromptEvent.asSharedFlow()

    private var lastAutoSwitchedId: Long = -1L
    private var lastPromptedHuertoId: Long = -1L

    private val refreshMutex = Mutex()
    private var pollJob: Job? = null
    private var inForeground = false

    init {
        viewModelScope.launch {
            repo.pruneOld()
            repo.ensureDemoData()
            reloadHuertos()
            reloadAlerts()
        }
    }

    // ------------------------------------------------------------ Selección

    private suspend fun reloadHuertos(preferId: Long? = null) {
        val list = repo.huertos()
        _huertos.value = list
        val wanted = preferId ?: _selected.value?.id ?: prefs.selectedHuertoId
        applySelection(list.firstOrNull { it.id == wanted } ?: list.firstOrNull())
    }

    private suspend fun applySelection(h: Huerto?) {
        val changed = _selected.value?.id != h?.id
        _selected.value = h
        prefs.selectedHuertoId = h?.id ?: -1L
        if (changed) {
            _reading.value = if (h != null) repo.latest(h.id) else null
            _irrigations.value = if (h != null) repo.irrigations(h.id) else emptyList()
            _conn.value = Conn.Idle
        }
        restartPolling()
    }

    fun selectHuerto(id: Long) {
        viewModelScope.launch {
            applySelection(_huertos.value.firstOrNull { it.id == id })
        }
    }

    /**
     * Algoritmo de Geofencing para detección y cambio automático de lotes según la ubicación GPS.
     */
    fun onLocationUpdated(userLat: Double, userLng: Double) {
        if (!prefs.autoLotDetection) return
        val list = _huertos.value
        if (list.isEmpty()) return

        var matchedHuerto: Huerto? = null
        var minDistance = Double.MAX_VALUE

        // Evaluar por radio de geocerca del lote
        for (h in list) {
            if (h.latitude == 0.0 && h.longitude == 0.0) continue
            val dist = distanceMeters(userLat, userLng, h.latitude, h.longitude)
            if (dist <= h.radiusMeters && dist < minDistance) {
                minDistance = dist
                matchedHuerto = h
            }
        }

        matchedHuerto?.let { target ->
            val currentId = _selected.value?.id
            if (target.id != currentId && target.id != lastPromptedHuertoId) {
                lastPromptedHuertoId = target.id
                viewModelScope.launch {
                    _autoSwitchEvent.emit("📍 Geocerca GPS: Te encuentras en ${target.name}. Se cambió la vista automáticamente.")
                    applySelection(target)
                }
            }
        }
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }

    // -------------------------------------------------------------- Polling

    fun setForeground(value: Boolean) {
        inForeground = value
        if (value) restartPolling() else pollJob?.cancel()
    }

    private fun restartPolling() {
        pollJob?.cancel()
        if (!inForeground || _selected.value == null) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                doRefresh(showLoading = _reading.value == null)
                delay(prefs.pollSeconds * 1000L)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { doRefresh(showLoading = true) }
    }

    private suspend fun doRefresh(showLoading: Boolean) {
        refreshMutex.withLock {
            val h = _selected.value ?: return
            if (showLoading) _conn.value = Conn.Loading
            val result = repo.refresh(h)
            if (_selected.value?.id == h.id) {
                if (result.isSuccess) {
                    _reading.value = result.getOrNull()
                    _conn.value = Conn.Online
                } else {
                    val e = result.exceptionOrNull()
                    _conn.value = Conn.Error(e?.message ?: e?.javaClass?.simpleName ?: "?")
                }
            }
            reloadAlerts()
        }
    }

    // ------------------------------------------------------------- Consultas

    suspend fun history(sinceMs: Long, maxPoints: Int = 240): List<SensorReading> {
        val h = _selected.value ?: return emptyList()
        return repo.history(h.id, sinceMs, maxPoints)
    }

    suspend fun weekHistory(): List<SensorReading> =
        history(System.currentTimeMillis() - 7L * 24 * 3600 * 1000, 120)

    suspend fun testConnection(url: String): Result<SensorReading> = repo.testUrl(url)

    // -------------------------------------------------------------- Huertos

    fun saveHuerto(h: Huerto) {
        viewModelScope.launch {
            repo.saveHuerto(h)
            reloadHuertos()
        }
    }

    fun deleteHuerto(h: Huerto): Boolean {
        if (_huertos.value.size <= 1) return false
        viewModelScope.launch {
            repo.deleteHuerto(h.id)
            reloadHuertos()
            reloadAlerts()
        }
        return true
    }

    fun setCrop(cropId: String) {
        val h = _selected.value ?: return
        saveHuerto(h.copy(cropId = cropId))
    }

    // --------------------------------------------------------------- Riegos

    fun addIrrigation(minutes: Int, note: String) {
        val h = _selected.value ?: return
        viewModelScope.launch {
            repo.addIrrigation(h.id, minutes, note)
            _irrigations.value = repo.irrigations(h.id)
        }
    }

    // -------------------------------------------------------------- Alertas

    private suspend fun reloadAlerts() {
        _alerts.value = repo.alerts()
        _unread.value = repo.unreadCount()
    }

    fun markAlertsRead() {
        viewModelScope.launch {
            repo.markAlertsRead()
            reloadAlerts()
        }
    }

    fun clearAlerts() {
        viewModelScope.launch {
            repo.clearAlerts()
            reloadAlerts()
        }
    }

    // --------------------------------------------------------------- Ajustes

    fun onModeChanged() {
        viewModelScope.launch {
            repo.onModeChanged()
            _reading.value = _selected.value?.let { repo.latest(it.id) }
            _conn.value = Conn.Idle
            restartPolling()
        }
    }

    fun onPollChanged() = restartPolling()

    fun regenerateDemo() {
        viewModelScope.launch { repo.regenerateDemo() }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repo.clearHistory()
            _reading.value = null
        }
    }
}
