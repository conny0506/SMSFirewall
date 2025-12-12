package com.example.smsfirewall

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class SmsApp : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        // Android 8.0 (Oreo) ve üzeri için kanal oluşturmak şart
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Varsayılan SMS Kanalı"
            val descriptionText = "Gelen SMS bildirimleri"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("sms_channel_id", name, importance).apply {
                description = descriptionText
            }

            // Kanalı sisteme kaydet
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}