package com.example.location

import android.Manifest
import android.app.Notification
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
import com.example.MainActivity
import com.example.R

object NotificationHelper {

    const val CHANNEL_LOCATION = "together_location_sharing"
    const val CHANNEL_ALERTS = "together_partner_alerts"
    const val FOREGROUND_NOTIFICATION_ID = 1001

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val locationChannel = NotificationChannel(
                CHANNEL_LOCATION,
                context.getString(R.string.notif_channel_location_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_location_desc)
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.notif_channel_alerts_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_alerts_desc)
                enableVibration(true)
            }

            manager.createNotificationChannel(locationChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    fun buildLocationForegroundNotification(
        context: Context,
        statusText: String
    ): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_PAUSE_FROM_NOTIFICATION
        }
        val pausePending = PendingIntent.getService(
            context,
            1,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_LOCATION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Together GPS đang chia sẻ vị trí")
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .addAction(0, "Tạm dừng", pausePending)
            .build()
    }

    fun showPartnerAlertNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
