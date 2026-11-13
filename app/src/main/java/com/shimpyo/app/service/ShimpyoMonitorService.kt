package com.shimpyo.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.shimpyo.app.MainActivity
import com.shimpyo.app.R
import com.shimpyo.app.engine.ShimpyoEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Keeps the rest loop alive while the driver uses other apps (navigation, media).
 *
 * TODO(policy): Pleos background execution and boot-start policies are not public. Confirm the
 *  allowed foreground service type and whether auto-start on ignition is permitted before release.
 */
@AndroidEntryPoint
class ShimpyoMonitorService : Service() {

    @Inject lateinit var engine: ShimpyoEngine

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        engine.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_monitoring))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "rest_monitoring"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ShimpyoMonitorService::class.java)) }
        }
    }
}
