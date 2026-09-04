package com.sangwoo.push.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NotificationRepositoryInstrumentedTest {
    private lateinit var database: DeplDatabase
    private lateinit var repository: NotificationRepository

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), DeplDatabase::class.java
        ).build()
        repository = NotificationRepository(database.notificationDao())
    }

    @After fun close() = database.close()

    @Test fun deduplicatesAndKeepsOnlyNewestThreeThousand() = runBlocking {
        val duplicate = UUID.randomUUID().toString()
        val data = mapOf(
            "eventId" to duplicate,
            "notificationType" to "TASK_ARRIVED",
            "title" to "업무 알림",
            "body" to "업무가 도착 했습니다."
        )
        repository.ingest(data, 1)
        assertNull(repository.ingest(data, 2))
        repeat(3_005) { index ->
            repository.ingest(mapOf(
                "eventId" to UUID.randomUUID().toString(),
                "notificationType" to "TASK_ARRIVED",
                "title" to "업무 알림",
                "body" to "업무가 도착 했습니다."
            ), index.toLong() + 10)
        }
        assertEquals(3_000, repository.count())
    }

    @Test fun rejectsPayloadWithoutGatewayRenderedMessage() = runBlocking {
        val result = repository.ingest(mapOf(
            "eventId" to UUID.randomUUID().toString(),
            "notificationType" to "TASK_ARRIVED"
        ))

        assertNull(result)
        assertEquals(0, repository.count())
    }
}
