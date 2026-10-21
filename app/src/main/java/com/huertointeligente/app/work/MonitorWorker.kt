package com.huertointeligente.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.huertointeligente.app.App
import java.util.concurrent.TimeUnit

/** Revisa los sensores de todos los huertos aunque la app esté cerrada. */
class MonitorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as App).repository
        for (huerto in repo.huertos()) {
            repo.refresh(huerto)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "huerto_monitor"

        fun setEnabled(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (enabled) {
                val request = PeriodicWorkRequestBuilder<MonitorWorker>(15, TimeUnit.MINUTES).build()
                wm.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            } else {
                wm.cancelUniqueWork(UNIQUE_NAME)
            }
        }
    }
}
