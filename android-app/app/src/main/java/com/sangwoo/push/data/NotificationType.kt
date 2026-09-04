package com.sangwoo.push.data

enum class NotificationType(val wireName: String) {
    COMMENT_ADDED("COMMENT_ADDED"),
    TASK_MENTIONED("TASK_MENTIONED"),
    COMMENT_MENTIONED("COMMENT_MENTIONED"),
    SOURCE_CONFLICT("SOURCE_CONFLICT"),
    NOTICE_REGISTERED("NOTICE_REGISTERED"),
    TASK_ARRIVED("TASK_ARRIVED"),
    APPROVAL_TASK_ARRIVED("APPROVAL_TASK_ARRIVED"),
    MENTIONED_TASK_DEPLOYED("MENTIONED_TASK_DEPLOYED");

    fun channel(): NotificationChannelKind = when (this) {
        NOTICE_REGISTERED -> NotificationChannelKind.NOTICE
        APPROVAL_TASK_ARRIVED, SOURCE_CONFLICT -> NotificationChannelKind.IMPORTANT
        else -> NotificationChannelKind.GENERAL
    }

    companion object {
        fun fromWire(value: String?): NotificationType? = entries.firstOrNull { it.wireName == value }

    }
}

enum class NotificationChannelKind(val id: String) {
    // Channel settings cannot be raised after a channel has been created. Versioned
    // IDs ensure existing installations receive the new high-priority configuration.
    GENERAL("depl_general_v2"),
    NOTICE("depl_notice_v2"),
    IMPORTANT("depl_important_v2")
}
