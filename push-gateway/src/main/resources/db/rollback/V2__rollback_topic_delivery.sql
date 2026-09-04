-- Flyway Community는 이 파일을 자동 실행하지 않습니다. DBA 승인 후 수동 사용하십시오.
ALTER TABLE push_send_history DROP CONSTRAINT ck_push_history_delivery_channel,
    DROP CONSTRAINT ck_push_history_target_type, DROP KEY ix_push_history_target_sent,
    DROP COLUMN delivery_channel, DROP COLUMN target_id, DROP COLUMN target_type;
ALTER TABLE push_queue DROP CONSTRAINT ck_push_queue_delivery_channel,
    DROP CONSTRAINT ck_push_queue_target_type, DROP KEY ix_push_queue_target_status,
    DROP COLUMN delivery_channel, DROP COLUMN target_id, DROP COLUMN target_type;
DROP TABLE push_topic_binding;
