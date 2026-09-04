package com.sangwoo.push.messaging

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sangwoo.push.DeplApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class DeplFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val repository = (application as DeplApplication).notificationRepository
        // Keep the short database transaction inside the service callback so Android
        // cannot stop the process before the history row and local alert are committed.
        runBlocking(Dispatchers.IO) {
            repository.ingest(message.data)?.let { NotificationPresenter.show(this@DeplFirebaseMessagingService, it) }
        }
    }

    override fun onNewToken(token: String) {
        // Topic subscriptions are restored by Firebase. Never log or transmit the token.
    }
}
