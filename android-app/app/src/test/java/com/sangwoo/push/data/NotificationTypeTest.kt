package com.sangwoo.push.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTypeTest {
    @Test fun mapsAllEightWireValues() {
        val expected = setOf(
            "COMMENT_ADDED", "TASK_MENTIONED", "COMMENT_MENTIONED", "SOURCE_CONFLICT",
            "NOTICE_REGISTERED", "TASK_ARRIVED", "APPROVAL_TASK_ARRIVED", "MENTIONED_TASK_DEPLOYED"
        )
        assertEquals(expected, NotificationType.entries.map { it.wireName }.toSet())
        expected.forEach { assertEquals(it, NotificationType.fromWire(it)?.wireName) }
        assertNull(NotificationType.fromWire("UNKNOWN"))
    }

    @Test fun selectsChannels() {
        assertEquals(NotificationChannelKind.NOTICE, NotificationType.NOTICE_REGISTERED.channel())
        assertEquals(NotificationChannelKind.IMPORTANT, NotificationType.APPROVAL_TASK_ARRIVED.channel())
        assertEquals(NotificationChannelKind.GENERAL, NotificationType.TASK_ARRIVED.channel())
        assertEquals("depl_general_v2", NotificationChannelKind.GENERAL.id)
        assertEquals("depl_notice_v2", NotificationChannelKind.NOTICE.id)
        assertEquals("depl_important_v2", NotificationChannelKind.IMPORTANT.id)
    }
}
