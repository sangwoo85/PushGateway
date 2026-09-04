package com.sangwoo.push.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [NotificationEntity::class], version = 1, exportSchema = true)
abstract class DeplDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile private var instance: DeplDatabase? = null

        fun get(context: Context): DeplDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                DeplDatabase::class.java,
                "depl-notifications.db"
            ).build().also { instance = it }
        }
    }
}
