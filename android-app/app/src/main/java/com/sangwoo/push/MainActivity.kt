package com.sangwoo.push

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeEvent(intent?.getStringExtra(EXTRA_EVENT_ID))
        setContent { DeplApp(viewModel) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        consumeEvent(intent.getStringExtra(EXTRA_EVENT_ID))
    }

    private fun consumeEvent(eventId: String?) {
        if (eventId == null) return
        lifecycleScope.launch {
            (application as DeplApplication).notificationRepository.markRead(eventId)
        }
    }

    companion object { const val EXTRA_EVENT_ID = "eventId" }
}
