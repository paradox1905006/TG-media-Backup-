package com.dparadox.tgbackup

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * Application class — runs once when the app process starts.
 * Creates the notification channel needed for WorkManager foreground
 * service notifications (required on Android 8+).
 */
class TgBackupApplication : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "tg_backup_progress"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Android 8+ requires a notification channel to be declared before
     * showing any notification. We create one channel for backup progress.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW   // Low = no sound, no heads-up
            ).apply {
                description = "Shows upload progress during Telegram backup"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
