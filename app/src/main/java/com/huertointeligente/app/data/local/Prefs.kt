package com.huertointeligente.app.data.local

import android.content.Context
import com.huertointeligente.app.data.model.DataSourceMode

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("huerto_prefs", Context.MODE_PRIVATE)

    var mode: DataSourceMode
        get() = runCatching { DataSourceMode.valueOf(sp.getString(K_MODE, "") ?: "") }
            .getOrDefault(DataSourceMode.SIMULATED)
        set(value) = sp.edit().putString(K_MODE, value.name).apply()

    var pollSeconds: Int
        get() = sp.getInt(K_POLL, 15)
        set(value) = sp.edit().putInt(K_POLL, value).apply()

    var notificationsEnabled: Boolean
        get() = sp.getBoolean(K_NOTIF, true)
        set(value) = sp.edit().putBoolean(K_NOTIF, value).apply()

    var backgroundMonitoring: Boolean
        get() = sp.getBoolean(K_BG, false)
        set(value) = sp.edit().putBoolean(K_BG, value).apply()

    var selectedHuertoId: Long
        get() = sp.getLong(K_SELECTED, -1L)
        set(value) = sp.edit().putLong(K_SELECTED, value).apply()

    var isDarkMode: Boolean
        get() = sp.getBoolean(K_DARK_MODE, false)
        set(value) = sp.edit().putBoolean(K_DARK_MODE, value).apply()

    private companion object {
        const val K_MODE = "mode"
        const val K_POLL = "poll_seconds"
        const val K_NOTIF = "notifications"
        const val K_BG = "background"
        const val K_SELECTED = "selected_huerto"
        const val K_DARK_MODE = "dark_mode"
    }
}
