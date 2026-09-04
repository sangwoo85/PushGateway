package com.sangwoo.push.enrollment

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TopicRegistrationCoordinatorTest {
    @Test fun subscribesThreeTopicsAndReplacesOldOnReregistration() = runTest {
        val client = FakeClient()
        val store = FakeStore()
        val coordinator = TopicRegistrationCoordinator(client, store)
        coordinator.register(payload("old", "old"))
        coordinator.register(payload("new", "new"))
        assertEquals(setOf("usr_new", "dept_new", "notice_all"), client.active)
        assertEquals("usr_new", store.record.current?.user)
    }

    @Test fun rollsBackWhenOneSubscriptionFails() = runTest {
        val client = FakeClient(failSubscribe = "dept_new")
        val store = FakeStore()
        val coordinator = TopicRegistrationCoordinator(client, store)
        assertThrows(EnrollmentException.SubscriptionFailed::class.java) {
            kotlinx.coroutines.runBlocking { coordinator.register(payload("new", "new")) }
        }
        assertEquals(emptySet<String>(), client.active)
        assertNull(store.record.current)
    }

    private fun payload(user: String, dept: String) = EnrollmentQrPayload(
        1, "depl-162ae", EnrollmentTopics("usr_$user", "dept_$dept", "notice_all"),
        "", "", "", ""
    )

    private class FakeClient(private val failSubscribe: String? = null) : TopicClient {
        val active = mutableSetOf<String>()
        override suspend fun ensureToken() = Unit
        override suspend fun subscribe(topic: String) {
            if (topic == failSubscribe) error("fail")
            active += topic
        }
        override suspend fun unsubscribe(topic: String) { active -= topic }
    }

    private class FakeStore : EnrollmentRecordStore {
        var record = EnrollmentRecord()
        override suspend fun load() = record
        override suspend fun save(record: EnrollmentRecord) { this.record = record }
        override suspend fun clear() { record = EnrollmentRecord() }
    }
}
