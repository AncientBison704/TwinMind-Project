package com.example.twinmindproject.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.twinmindproject.MainActivity
import com.example.twinmindproject.R


object RecordingNotifications {
    const val CHANNEL_ID = "rec_channel_live"
    const val NOTIF_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Recording",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Live recording status"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(ch)
            } else {
                if (existing.importance < NotificationManager.IMPORTANCE_HIGH) {
                    existing.importance = NotificationManager.IMPORTANCE_HIGH
                    nm.createNotificationChannel(existing)
                }
            }
        }
    }

    fun buildLive(
        context: Context,
        title: String,
        subtitle: String,
        isRecording: Boolean,
        elapsedSec: Int,
        showResume: Boolean
    ): Notification {
        val openPending = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )

        fun serviceAction(action: String, code: Int) = PendingIntent.getService(
            context, code, Intent(context, RecordingService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )

        val stopPending = serviceAction(RecordingService.ACTION_STOP, 1)
        val pausePending = serviceAction(RecordingService.ACTION_PAUSE, 2)
        val resumePending = serviceAction(RecordingService.ACTION_RESUME, 3)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (isRecording) {
            val baseMs = System.currentTimeMillis() - (elapsedSec * 1000L)
            builder.setUsesChronometer(true)
                .setWhen(baseMs)
                .setChronometerCountDown(false)
        } else {
            builder.setUsesChronometer(false)
        }

        if (isRecording) {
            builder.addAction(0, "Pause", pausePending)
        } else if (showResume) {
            builder.addAction(0, "Resume", resumePending)
        }
        builder.addAction(R.drawable.baseline_stop_circle_24, "Stop", stopPending)

        if (Build.VERSION.SDK_INT >= 34) {
            builder.setForegroundServiceBehavior(
                NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
            )
        }

        return builder.build()
    }


    fun build(
        context: Context,
        title: String,
        text: String
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPending = PendingIntent.getService(
            context,
            1,
            Intent(context, RecordingService::class.java).apply { action = RecordingService.ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // better on lock screen
            .setContentIntent(openIntent)
            .addAction(R.drawable.baseline_stop_circle_24, "Stop", stopPending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColorized(true)
            .setColor(Color.RED)

        if (Build.VERSION.SDK_INT >= 34) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    fun buildPausedWithResumeStop(
        context: Context,
        title: String,
        text: String
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPending = PendingIntent.getService(
            context, 1,
            Intent(context, RecordingService::class.java).apply { action = RecordingService.ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val resumePending = PendingIntent.getService(
            context, 2,
            Intent(context, RecordingService::class.java).apply { action = RecordingService.ACTION_RESUME },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openIntent)
            .addAction(0, "Resume", resumePending)
            .addAction(R.drawable.baseline_stop_circle_24, "Stop", stopPending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColorized(true)
            .setColor(Color.RED)

        if (Build.VERSION.SDK_INT >= 34) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun mutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_MUTABLE else 0

    fun notify(context: Context, notification: Notification) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        try {
            nm.notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {
        }
    }
}

