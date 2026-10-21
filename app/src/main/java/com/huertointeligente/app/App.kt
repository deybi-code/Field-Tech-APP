package com.huertointeligente.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.huertointeligente.app.data.Repository
import com.huertointeligente.app.data.local.AppDatabase
import com.huertointeligente.app.data.local.Prefs
import com.huertointeligente.app.notifications.Notifier

class App : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        
        // Aplicar el modo oscuro según la preferencia
        val mode = if (prefs.isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(mode)

        repository = Repository(this, AppDatabase(this), prefs)
        Notifier.createChannel(this)
    }
}
