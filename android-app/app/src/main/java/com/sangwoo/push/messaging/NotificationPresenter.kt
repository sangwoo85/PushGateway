package com.sangwoo.push.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
        listOf(
            NotificationChannel(NotificationChannelKind.GENERAL.id, "일반 업무 알림", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(NotificationChannelKind.NOTICE.id, "공지 알림", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(NotificationChannelKind.IMPORTANT.id, "결재·중요 알림", NotificationManager.IMPORTANCE_HIGH)
        ).forEach {
            it.description = "업무 상세 없이 DEPL 알림 종류만 표시합니다."
            it.lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
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
            .setContentTitle("업무 알림")
            .setContentText(notification.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.message))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
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
