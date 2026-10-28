package com.vaydelux.bousygram.core.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.R

/** Visible, user-initiated process protection while a remote receiver fetches media. */
class CastStreamingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Casting playback", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!BuildConfig.PRIVATE_MEDIA_CAST_ENABLED || intent?.action != ACTION_START) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.app_name))
        .setContentText("Streaming to a Cast receiver")
        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    companion object {
        private const val CHANNEL_ID = "cast_streaming"
        private const val NOTIFICATION_ID = 4701
        private const val ACTION_START = "com.vaydelux.bousygram.cast.START_STREAMING"

        fun start(context: Context) {
            if (!BuildConfig.PRIVATE_MEDIA_CAST_ENABLED) return
            val intent = Intent(context, CastStreamingService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CastStreamingService::class.java))
        }
    }
}
