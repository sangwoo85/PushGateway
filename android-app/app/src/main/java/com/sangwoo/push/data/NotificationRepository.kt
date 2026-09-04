package com.sangwoo.push.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class NotificationRepository(private val dao: NotificationDao) {
    val history: Flow<PagingData<NotificationEntity>> = Pager(
        config = PagingConfig(pageSize = 30, prefetchDistance = 10, enablePlaceholders = false),
        pagingSourceFactory = dao::pagingSource
    ).flow

    suspend fun ingest(data: Map<String, String>, receivedAt: Long = System.currentTimeMillis()): IngestedNotification? {
        val eventId = data["eventId"]?.takeIf { runCatching { UUID.fromString(it) }.isSuccess } ?: return null
        val type = NotificationType.fromWire(data["notificationType"]) ?: return null
        val actor = data["actorName"]?.let(NotificationType::normalizeActor)
        val message = type.message(actor) ?: return null
        val entity = NotificationEntity(
            eventId = eventId,
            notificationType = type.wireName,
            actorName = if (type.actorRequired) actor else null,
            receivedAt = receivedAt
        )
        return if (dao.insertAndPrune(entity)) IngestedNotification(eventId, type, message) else null
    }

    suspend fun markRead(eventId: String) = dao.markRead(eventId)
    suspend fun clear() = dao.clear()
    suspend fun count(): Int = dao.count()
}

data class IngestedNotification(
    val eventId: String,
    val type: NotificationType,
    val message: String
)
