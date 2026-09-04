package com.sangwoo.push.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NotificationEntity::class], version = 2, exportSchema = true)
abstract class DeplDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile private var instance: DeplDatabase? = null

        fun get(context: Context): DeplDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                DeplDatabase::class.java,
                "depl-notifications.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notification_history ADD COLUMN title TEXT NOT NULL DEFAULT '업무 알림'")
                db.execSQL("ALTER TABLE notification_history ADD COLUMN body TEXT NOT NULL DEFAULT '업무 알림이 도착했습니다.'")
            }
        }
    }
}
