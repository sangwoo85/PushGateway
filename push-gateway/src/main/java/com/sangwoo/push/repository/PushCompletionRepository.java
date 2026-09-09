package com.sangwoo.push.repository;

import com.sangwoo.push.domain.Platform;
import com.sangwoo.push.domain.NotificationType;
import com.sangwoo.push.domain.PushQueueItem;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PushCompletionRepository {
    private final JdbcTemplate jdbc;

    public PushCompletionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void completeAccepted(PushQueueItem item, Platform platform, String messageId,
                                 String resultCode, Instant requestedAt, Instant sentAt) {
        jdbc.update("""
                INSERT INTO push_send_history
                    (event_id, target_type, target_id, delivery_channel, recipient_user_id,
                     notification_type, actor_name, platform, firebase_message_id, result_code,
                     requested_at, sent_at)
                VALUES (?, ?, ?, 'TOPIC', ?, ?, ?, ?, ?, ?, ?, ?)
                """, item.eventId().toString(), item.targetType().name(), item.targetId(), item.targetId(),
                item.notificationType().name(),
                item.actorName(), platform.name(), messageId, resultCode,
                Timestamp.from(requestedAt), Timestamp.from(sentAt));
        int deleted = jdbc.update("""
                DELETE FROM push_queue
                 WHERE id = ? AND status = 'PROCESSING' AND locked_by = ?
                """, item.id(), item.lockedBy());
        if (deleted != 1) {
            throw new IllegalStateException("Claimed queue item was not deleted: " + item.eventId());
        }
    }

    public List<PushHistoryEntry> findRecentForUser(String userId, int limit) {
        return jdbc.query("""
                SELECT event_id, notification_type, actor_name, sent_at
                  FROM push_send_history
                 WHERE recipient_user_id = ?
                 ORDER BY sent_at DESC, id DESC
                 LIMIT ?
                """, (rs, rowNum) -> new PushHistoryEntry(
                        UUID.fromString(rs.getString("event_id")),
                        NotificationType.valueOf(rs.getString("notification_type")),
                        rs.getString("actor_name"),
                        rs.getTimestamp("sent_at").toInstant()), userId, limit);
    }

    public record PushHistoryEntry(UUID eventId, NotificationType notificationType,
                                   String actorName, Instant sentAt) {}
}
