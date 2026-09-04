package com.sangwoo.push

import android.app.Application
import com.sangwoo.push.data.DeplDatabase
import com.sangwoo.push.data.NotificationRepository
import com.sangwoo.push.enrollment.EnrollmentStore
import com.sangwoo.push.enrollment.FirebaseTopicClient
import com.sangwoo.push.enrollment.TopicRegistrationCoordinator
import com.sangwoo.push.messaging.NotificationPresenter

class DeplApplication : Application() {
    val notificationRepository by lazy { NotificationRepository(DeplDatabase.get(this).notificationDao()) }
    val enrollmentStore by lazy { EnrollmentStore(this) }
    val topicCoordinator by lazy { TopicRegistrationCoordinator(FirebaseTopicClient(), enrollmentStore) }

    override fun onCreate() {
        super.onCreate()
        NotificationPresenter.createChannels(this)
    }
}
