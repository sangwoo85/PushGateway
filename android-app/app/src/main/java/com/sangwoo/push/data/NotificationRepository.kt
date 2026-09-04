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
        val title = normalizeText(data["title"], 50) ?: return null
        val body = normalizeText(data["body"], 200) ?: return null
        val actor = data["actorName"]?.let { normalizeText(it, 50) }
        val entity = NotificationEntity(
            eventId = eventId,
            notificationType = type.wireName,
            actorName = actor,
            title = title,
            body = body,
            receivedAt = receivedAt
        )
        return if (dao.insertAndPrune(entity)) IngestedNotification(eventId, type, title, body) else null
    }

    suspend fun markRead(eventId: String) = dao.markRead(eventId)
    suspend fun clear() = dao.clear()
    suspend fun count(): Int = dao.count()

    private fun normalizeText(value: String?, maximum: Int): String? = value
        ?.replace(Regex("[\\p{Cc}]"), " ")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.take(maximum)
}

data class IngestedNotification(
    val eventId: String,
    val type: NotificationType,
    val title: String,
    val body: String
)
