CREATE TABLE push_queue (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id CHAR(36) NOT NULL,
    recipient_user_id VARCHAR(128) NOT NULL,
    notification_type VARCHAR(64) NOT NULL,
    actor_name VARCHAR(50) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    locked_by VARCHAR(128) NULL,
    locked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_error_code VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_push_queue_event_id (event_id),
    KEY ix_push_queue_claim (status, next_attempt_at, created_at),
    CONSTRAINT ck_push_queue_status CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY', 'DEAD')),
    CONSTRAINT ck_push_queue_type CHECK (notification_type IN (
        'TASK_COMMENT_CREATED', 'TASK_MENTIONED', 'COMMENT_MENTIONED', 'SOURCE_OVERLAP',
        'NOTICE_CREATED', 'TASK_ARRIVED', 'APPROVAL_TASK_ARRIVED', 'MENTIONED_TASK_DEPLOYED'
    )),
    CONSTRAINT ck_push_queue_attempt CHECK (attempt_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE push_send_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id CHAR(36) NOT NULL,
    recipient_user_id VARCHAR(128) NOT NULL,
    notification_type VARCHAR(64) NOT NULL,
    actor_name VARCHAR(50) NULL,
    platform VARCHAR(16) NOT NULL,
    firebase_message_id VARCHAR(255) NULL,
    result_code VARCHAR(64) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_push_history_event_id (event_id),
    KEY ix_push_history_recipient_sent (recipient_user_id, sent_at),
    CONSTRAINT ck_push_history_platform CHECK (platform IN ('IOS', 'ANDROID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE push_device (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(128) NOT NULL,
    platform VARCHAR(16) NOT NULL,
    firebase_registration_id VARCHAR(4096) NOT NULL,
    app_instance_id VARCHAR(128) NOT NULL,
    app_version VARCHAR(32) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_push_device_user_instance (user_id, app_instance_id),
    UNIQUE KEY uk_push_device_registration (firebase_registration_id(255)),
    KEY ix_push_device_user_enabled (user_id, enabled),
    CONSTRAINT ck_push_device_platform CHECK (platform IN ('IOS', 'ANDROID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
