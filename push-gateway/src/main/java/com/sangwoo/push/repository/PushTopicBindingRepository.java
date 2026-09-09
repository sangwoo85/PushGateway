package com.sangwoo.push.repository;

import com.sangwoo.push.domain.PushTargetType;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PushTopicBindingRepository {
    private final JdbcTemplate jdbc;

    public PushTopicBindingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> findEnabled(PushTargetType type, String targetId) {
        return jdbc.query("""
                SELECT topic_name FROM push_topic_binding
                 WHERE target_type = ? AND target_id = ? AND enabled = TRUE
                """, rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(),
                type.name(), targetId);
    }

    /** unique 충돌 뒤 REPEATABLE READ snapshot을 피하고 방금 확정된 승자 행을 읽는다. */
    public Optional<String> findEnabledForUpdate(PushTargetType type, String targetId) {
        return jdbc.query("""
                SELECT topic_name FROM push_topic_binding
                 WHERE target_type = ? AND target_id = ? AND enabled = TRUE
                 FOR UPDATE
                """, rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(),
                type.name(), targetId);
    }

    public void insert(PushTargetType type, String targetId, String topic) throws DuplicateKeyException {
        jdbc.update("""
                INSERT INTO push_topic_binding (target_type, target_id, topic_name, enabled)
                VALUES (?, ?, ?, TRUE)
                """, type.name(), targetId, topic);
    }
}
