package com.sangwoo.push.enrollment

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

interface TopicClient {
    suspend fun ensureToken()
    suspend fun subscribe(topic: String)
    suspend fun unsubscribe(topic: String)
}

class FirebaseTopicClient(private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance()) : TopicClient {
    override suspend fun ensureToken() { messaging.token.await() }
    override suspend fun subscribe(topic: String) { messaging.subscribeToTopic(topic).await() }
    override suspend fun unsubscribe(topic: String) { messaging.unsubscribeFromTopic(topic).await() }
}

class TopicRegistrationCoordinator(
    private val client: TopicClient,
    private val store: EnrollmentRecordStore
) {
    suspend fun isRegistered(): Boolean = store.load().current != null

    suspend fun register(payload: EnrollmentQrPayload) {
        client.ensureToken()
        val previous = store.load()
        retryCleanup(previous)
        val currentTopics = previous.current?.all()?.map { it.second }.orEmpty().toSet()
        val additions = payload.topics.all().filterNot { it.second in currentTopics }
        val completed = mutableListOf<Pair<TopicKind, String>>()
        try {
            for (topic in additions) {
                client.subscribe(topic.second)
                completed += topic
            }
        } catch (_: Exception) {
            completed.asReversed().forEach { runCatching { client.unsubscribe(it.second) } }
            val failedKind = additions.getOrNull(completed.size)?.first ?: TopicKind.USER
            throw EnrollmentException.SubscriptionFailed(failedKind)
        }

        val nextSet = payload.topics.all().map { it.second }.toSet()
        val cleanup = (currentTopics - nextSet) + previous.cleanup
        store.save(EnrollmentRecord(payload.topics, cleanup))
        retryCleanup(store.load())
    }

    suspend fun reset() {
        val record = store.load()
        val topics = record.current?.all()?.map { it.second }.orEmpty().toSet() + record.cleanup
        var failed = false
        topics.forEach { if (runCatching { client.unsubscribe(it) }.isFailure) failed = true }
        if (failed) throw EnrollmentException.ResetFailed()
        store.clear()
    }

    private suspend fun retryCleanup(record: EnrollmentRecord) {
        if (record.cleanup.isEmpty()) return
        val remaining = record.cleanup.filterTo(mutableSetOf()) {
            runCatching { client.unsubscribe(it) }.isFailure
        }
        store.save(record.copy(cleanup = remaining))
    }
}
