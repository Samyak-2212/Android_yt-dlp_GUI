package com.ytdlp.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.ytdlp.ffmpeg.FFmpegBridge

class YtDlpApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initFFmpeg()
        createNotificationChannels()
    }

    private fun initFFmpeg() {
        try {
            FFmpegBridge.init(fromJniLibs = true)
        } catch (e: Throwable) {
            Log.w("YtDlpApp", "Bundled FFmpeg JNI load failed; will fallback to FFmpegLoader if needed", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress and completion notifications for active media downloads"
                setShowBadge(false)
            }

            val updateChannel = NotificationChannel(
                CHANNEL_UPDATES,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for available updates"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(updateChannel)
        }
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "downloads_channel"
        const val CHANNEL_UPDATES = "updates_channel"
    }
}
