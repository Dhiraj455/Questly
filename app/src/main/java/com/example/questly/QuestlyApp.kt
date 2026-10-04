package com.example.questly

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

const val ACTIVITY_CHANNEL_ID = "questly_activity"

@HiltAndroidApp
class QuestlyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The notification channel must exist before any FCM message is shown (Android 8+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ACTIVITY_CHANNEL_ID,
                "Friend activity",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Check-ins and activity from your friends" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
