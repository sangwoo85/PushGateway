CREATE TABLE push_topic_binding (
    id BIGINT NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(16) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    topic_name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_push_topic_target (target_type, target_id),
    UNIQUE KEY uk_push_topic_name (topic_name),
    KEY ix_push_topic_enabled_target (enabled, target_type, target_id),
    CONSTRAINT ck_push_topic_target_type CHECK (target_type IN ('USER', 'DEPARTMENT', 'NOTICE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO push_topic_binding (target_type, target_id, topic_name, enabled)
VALUES ('NOTICE', 'ALL', 'notice_all', TRUE);

ALTER TABLE push_queue
    ADD COLUMN target_type VARCHAR(16) NULL AFTER event_id,
    ADD COLUMN target_id VARCHAR(128) NULL AFTER target_type,
    ADD COLUMN delivery_channel VARCHAR(16) NOT NULL DEFAULT 'TOPIC' AFTER target_id;

UPDATE push_queue
   SET target_type = 'USER', target_id = recipient_user_id
 WHERE target_type IS NULL;

ALTER TABLE push_queue
    MODIFY target_type VARCHAR(16) NOT NULL,
    MODIFY target_id VARCHAR(128) NOT NULL,
    ADD KEY ix_push_queue_target_status (target_type, status, next_attempt_at),
    ADD CONSTRAINT ck_push_queue_target_type CHECK (target_type IN ('USER', 'DEPARTMENT', 'NOTICE')),
    ADD CONSTRAINT ck_push_queue_delivery_channel CHECK (delivery_channel = 'TOPIC');

ALTER TABLE push_send_history
    ADD COLUMN target_type VARCHAR(16) NULL AFTER event_id,
    ADD COLUMN target_id VARCHAR(128) NULL AFTER target_type,
    ADD COLUMN delivery_channel VARCHAR(16) NOT NULL DEFAULT 'TOPIC' AFTER target_id;

UPDATE push_send_history
   SET target_type = 'USER', target_id = recipient_user_id
 WHERE target_type IS NULL;

ALTER TABLE push_send_history
    MODIFY target_type VARCHAR(16) NOT NULL,
    MODIFY target_id VARCHAR(128) NOT NULL,
    ADD KEY ix_push_history_target_sent (target_type, target_id, sent_at),
    ADD CONSTRAINT ck_push_history_target_type CHECK (target_type IN ('USER', 'DEPARTMENT', 'NOTICE')),
    ADD CONSTRAINT ck_push_history_delivery_channel CHECK (delivery_channel = 'TOPIC');

