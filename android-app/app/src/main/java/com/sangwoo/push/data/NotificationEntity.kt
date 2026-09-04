package com.sangwoo.push.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_history",
    indices = [Index(value = ["receivedAt"]), Index(value = ["eventId"], unique = true)]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String,
    val notificationType: String,
    val actorName: String?,
    val title: String,
    val body: String,
    val receivedAt: Long,
    val isRead: Boolean = false
) {
    fun type(): NotificationType? = NotificationType.fromWire(notificationType)
}
