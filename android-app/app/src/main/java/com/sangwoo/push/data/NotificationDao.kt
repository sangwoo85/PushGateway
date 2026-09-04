package com.sangwoo.push.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: NotificationEntity): Long

    @Query("SELECT * FROM notification_history ORDER BY receivedAt DESC, id DESC")
    fun pagingSource(): PagingSource<Int, NotificationEntity>

    @Query("SELECT COUNT(*) FROM notification_history")
    suspend fun count(): Int

    @Query("DELETE FROM notification_history")
    suspend fun clear()

    @Query("UPDATE notification_history SET isRead = 1 WHERE eventId = :eventId")
    suspend fun markRead(eventId: String)

    @Query("DELETE FROM notification_history WHERE id NOT IN (SELECT id FROM notification_history ORDER BY receivedAt DESC, id DESC LIMIT :maximum)")
    suspend fun prune(maximum: Int)

    @Transaction
    suspend fun insertAndPrune(entity: NotificationEntity, maximum: Int = 3_000): Boolean {
        val inserted = insert(entity) != -1L
        if (inserted) prune(maximum)
        return inserted
    }
}
