package com.sangwoo.push.data

import java.text.Normalizer

enum class NotificationType(val wireName: String, val actorRequired: Boolean = false) {
    COMMENT_ADDED("COMMENT_ADDED"),
    TASK_MENTIONED("TASK_MENTIONED", true),
    COMMENT_MENTIONED("COMMENT_MENTIONED", true),
    SOURCE_CONFLICT("SOURCE_CONFLICT"),
    NOTICE_REGISTERED("NOTICE_REGISTERED"),
    TASK_ARRIVED("TASK_ARRIVED"),
    APPROVAL_TASK_ARRIVED("APPROVAL_TASK_ARRIVED"),
    MENTIONED_TASK_DEPLOYED("MENTIONED_TASK_DEPLOYED");

    fun message(actorName: String?): String? {
        val actor = actorName?.let(::normalizeActor)
        if (actorRequired && actor == null) return null
        return when (this) {
            COMMENT_ADDED -> "본인 업무에 댓글이 작성 되었습니다."
            TASK_MENTIONED -> "$actor 님이 업무에 당신을 언급하였습니다."
            COMMENT_MENTIONED -> "$actor 님이 댓글에 당신을 언급 하였습니다."
            SOURCE_CONFLICT -> "소스 겹침 알림"
            NOTICE_REGISTERED -> "공지 사항이 등록 되었습니다."
            TASK_ARRIVED -> "업무가 도착 했습니다."
            APPROVAL_TASK_ARRIVED -> "결재할 업무가 도착 했습니다."
            MENTIONED_TASK_DEPLOYED -> "당신이 언급된 업무가 운영에 반영 되었습니다."
        }
    }

    fun channel(): NotificationChannelKind = when (this) {
        NOTICE_REGISTERED -> NotificationChannelKind.NOTICE
        APPROVAL_TASK_ARRIVED, SOURCE_CONFLICT -> NotificationChannelKind.IMPORTANT
        else -> NotificationChannelKind.GENERAL
    }

    companion object {
        fun fromWire(value: String?): NotificationType? = entries.firstOrNull { it.wireName == value }

        fun normalizeActor(value: String): String? {
            val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace(Regex("[\\p{Cc}]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            return normalized.takeIf { it.isNotEmpty() }?.take(50)
        }
    }
}

enum class NotificationChannelKind(val id: String) {
    GENERAL("depl_general"),
    NOTICE("depl_notice"),
    IMPORTANT("depl_important")
}
