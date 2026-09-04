-- Flyway Community는 자동 down migration을 실행하지 않는다.
-- 운영 롤백 승인 후, 애플리케이션을 중지하고 백업한 상태에서 수동 실행한다.
DROP TABLE IF EXISTS push_send_history;
DROP TABLE IF EXISTS push_device;
DROP TABLE IF EXISTS push_queue;

