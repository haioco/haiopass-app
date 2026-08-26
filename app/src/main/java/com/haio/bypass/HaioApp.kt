package com.haio.bypass

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log

class HaioApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "HaioBypass VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for HaioBypass VPN service"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "haio_vpn_channel"
        private const val TAG = "HaioApp"
    }
}
