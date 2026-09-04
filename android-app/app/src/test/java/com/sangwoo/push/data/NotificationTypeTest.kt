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

    @Test fun mentionRequiresAndNormalizesActor() {
        assertNull(NotificationType.TASK_MENTIONED.message("  "))
        assertEquals("김 상우 님이 댓글에 당신을 언급 하였습니다.",
            NotificationType.COMMENT_MENTIONED.message("  김\n상우  "))
        assertEquals("업무가 도착 했습니다.", NotificationType.TASK_ARRIVED.message("무시"))
    }

    @Test fun selectsChannels() {
        assertEquals(NotificationChannelKind.NOTICE, NotificationType.NOTICE_REGISTERED.channel())
        assertEquals(NotificationChannelKind.IMPORTANT, NotificationType.APPROVAL_TASK_ARRIVED.channel())
        assertEquals(NotificationChannelKind.GENERAL, NotificationType.TASK_ARRIVED.channel())
    }
}
