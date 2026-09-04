package com.example.pushgateway.repository;

import com.example.pushgateway.domain.NotificationType;
import com.example.pushgateway.domain.PushQueueItem;
import com.example.pushgateway.domain.QueueStatus;
import com.example.pushgateway.domain.PushTargetType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PushQueueRepository {
    private final JdbcTemplate jdbc;

    public PushQueueRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID eventId, PushTargetType targetType, String targetId,
                       NotificationType type, String actorName) {
        jdbc.update("""
                INSERT INTO push_queue
                    (event_id, target_type, target_id, delivery_channel, recipient_user_id,
                     notification_type, actor_name, status, next_attempt_at)
                VALUES (?, ?, ?, 'TOPIC', ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP(6))
                """, eventId.toString(), targetType.name(), targetId, targetId, type.name(), actorName);
    }

    @Transactional
    public List<PushQueueItem> claimBatch(String workerId, Instant now, Instant staleBefore, int batchSize) {
        jdbc.update("""
                UPDATE push_queue
                   SET status = 'RETRY', locked_by = NULL, locked_at = NULL,
                       next_attempt_at = ?, last_error_code = 'STALE_PROCESSING_RECOVERED'
                 WHERE status = 'PROCESSING' AND locked_at < ?
                """, Timestamp.from(now), Timestamp.from(staleBefore));

        List<PushQueueItem> items = jdbc.query("""
                SELECT id, event_id, target_type, target_id, notification_type, actor_name,
                       status, attempt_count, created_at, locked_by
                  FROM push_queue
                 WHERE status IN ('PENDING', 'RETRY') AND next_attempt_at <= ?
                 ORDER BY created_at, id
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
                """, this::map, Timestamp.from(now), batchSize);

        for (PushQueueItem item : items) {
            jdbc.update("""
                    UPDATE push_queue
                       SET status = 'PROCESSING', locked_by = ?, locked_at = ?, last_error_code = NULL
                     WHERE id = ?
                    """, workerId, Timestamp.from(now), item.id());
        }
        return items.stream()
                .map(item -> new PushQueueItem(item.id(), item.eventId(), item.targetType(), item.targetId(),
                        item.notificationType(), item.actorName(), QueueStatus.PROCESSING,
                        item.attemptCount(), item.createdAt(), workerId))
                .toList();
    }

    public void markRetry(PushQueueItem item, int attemptCount, Instant nextAttemptAt, String errorCode) {
        jdbc.update("""
                UPDATE push_queue
                   SET status = 'RETRY', attempt_count = ?, next_attempt_at = ?,
                       locked_by = NULL, locked_at = NULL, last_error_code = ?
                 WHERE id = ? AND status = 'PROCESSING' AND locked_by = ?
                """, attemptCount, Timestamp.from(nextAttemptAt), errorCode, item.id(), item.lockedBy());
    }

    public void markDead(PushQueueItem item, int attemptCount, String errorCode) {
        jdbc.update("""
                UPDATE push_queue
                   SET status = 'DEAD', attempt_count = ?, locked_by = NULL, locked_at = NULL,
                       last_error_code = ?
                 WHERE id = ? AND status = 'PROCESSING' AND locked_by = ?
                """, attemptCount, errorCode, item.id(), item.lockedBy());
    }

    public long countWaiting() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM push_queue WHERE status IN ('PENDING', 'RETRY')", Long.class);
        return count == null ? 0 : count;
    }

    public long countByStatus(QueueStatus status) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM push_queue WHERE status = ?",
                Long.class, status.name());
        return count == null ? 0 : count;
    }

    public long countWaiting(PushTargetType type) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM push_queue
                 WHERE target_type = ? AND status IN ('PENDING', 'RETRY')
                """, Long.class, type.name());
        return count == null ? 0 : count;
    }

    public QueueView findByEventId(UUID eventId) {
        return jdbc.query("""
                SELECT q.event_id, q.target_type, q.target_id, q.notification_type, q.status,
                       q.attempt_count, q.last_error_code, q.created_at, NULL AS sent_at, NULL AS result_code
                  FROM push_queue q WHERE q.event_id = ?
                UNION ALL
                SELECT h.event_id, h.target_type, h.target_id, h.notification_type, 'SENT',
                       1, NULL, h.requested_at, h.sent_at, h.result_code
                  FROM push_send_history h WHERE h.event_id = ?
                LIMIT 1
                """, rs -> rs.next() ? new QueueView(UUID.fromString(rs.getString("event_id")),
                        PushTargetType.valueOf(rs.getString("target_type")), rs.getString("target_id"),
                        NotificationType.valueOf(rs.getString("notification_type")), rs.getString("status"),
                        rs.getInt("attempt_count"), rs.getString("last_error_code"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("sent_at") == null ? null : rs.getTimestamp("sent_at").toInstant(),
                        rs.getString("result_code")) : null, eventId.toString(), eventId.toString());
    }

    private PushQueueItem map(ResultSet rs, int rowNum) throws SQLException {
        return new PushQueueItem(
                rs.getLong("id"), UUID.fromString(rs.getString("event_id")),
                PushTargetType.valueOf(rs.getString("target_type")), rs.getString("target_id"),
                NotificationType.valueOf(rs.getString("notification_type")),
                rs.getString("actor_name"), QueueStatus.valueOf(rs.getString("status")),
                rs.getInt("attempt_count"), rs.getTimestamp("created_at").toInstant(),
                rs.getString("locked_by"));
    }

    public record QueueView(UUID eventId, PushTargetType targetType, String targetId,
                            NotificationType notificationType, String status, int attemptCount,
                            String lastErrorCode, Instant requestedAt, Instant processedAt,
                            String resultCode) {}
}
