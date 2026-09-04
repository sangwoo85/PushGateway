package com.sangwoo.push.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sangwoo.push.MainActivity
import com.sangwoo.push.R
import com.sangwoo.push.data.IngestedNotification
import com.sangwoo.push.data.NotificationChannelKind

object NotificationPresenter {
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val vibration = longArrayOf(0, 250, 150, 250)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        listOf(
            NotificationChannel(NotificationChannelKind.GENERAL.id, "일반 업무 알림", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(NotificationChannelKind.NOTICE.id, "공지 알림", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(NotificationChannelKind.IMPORTANT.id, "결재·중요 알림", NotificationManager.IMPORTANCE_HIGH)
        ).forEach {
            it.description = "업무 상세 없이 DEPL 알림 종류만 표시합니다."
            it.enableVibration(true)
            it.vibrationPattern = vibration
            it.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, audioAttributes)
            it.enableLights(true)
            it.setShowBadge(true)
            it.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            manager.createNotificationChannel(it)
        }
    }

    fun show(context: Context, notification: IngestedNotification) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_EVENT_ID, notification.eventId)
        }
        val pending = PendingIntent.getActivity(
            context,
            notification.eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val built = NotificationCompat.Builder(context, notification.type.channel().id)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.depl_yellow))
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        if (android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(notification.eventId.hashCode(), built)
        }
    }

}
