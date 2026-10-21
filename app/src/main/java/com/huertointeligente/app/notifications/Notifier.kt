package com.huertointeligente.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.huertointeligente.app.MainActivity
import com.huertointeligente.app.R
import com.huertointeligente.app.data.model.AlertItem
import com.huertointeligente.app.data.model.Huerto

object Notifier {

    private const val CHANNEL_ALERTS = "alerts"

    fun createChannel(ctx: Context) {
        val channel = NotificationChannel(
            CHANNEL_ALERTS,
            ctx.getString(R.string.channel_alerts),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = ctx.getString(R.string.channel_alerts_desc) }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, huerto: Huerto, alert: AlertItem) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setContentTitle("${huerto.name}: ${alert.title}")
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        val id = (alert.type.ordinal + 1) * 1000 + (huerto.id % 1000).toInt()
        NotificationManagerCompat.from(ctx).notify(id, notification)
    }
}
