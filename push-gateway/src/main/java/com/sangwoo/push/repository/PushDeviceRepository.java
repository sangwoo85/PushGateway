package com.sangwoo.push.repository;

import com.sangwoo.push.domain.Platform;
import com.sangwoo.push.domain.PushDevice;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Deprecated(forRemoval = true)
public class PushDeviceRepository {
    private final JdbcTemplate jdbc;

    public PushDeviceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void upsertIos(String userId, String registrationId, String appInstanceId, String appVersion) {
        jdbc.update("DELETE FROM push_device WHERE firebase_registration_id = ? AND user_id <> ?",
                registrationId, userId);
        jdbc.update("""
                INSERT INTO push_device
                    (user_id, platform, firebase_registration_id, app_instance_id, app_version, enabled)
                VALUES (?, 'IOS', ?, ?, ?, TRUE)
                ON DUPLICATE KEY UPDATE
                    firebase_registration_id = VALUES(firebase_registration_id),
                    app_version = VALUES(app_version), enabled = TRUE, updated_at = CURRENT_TIMESTAMP(6)
                """, userId, registrationId, appInstanceId, appVersion);
    }

    public int disableForUser(String userId, String appInstanceId) {
        return jdbc.update("""
                UPDATE push_device SET enabled = FALSE, updated_at = CURRENT_TIMESTAMP(6)
                 WHERE user_id = ? AND app_instance_id = ?
                """, userId, appInstanceId);
    }

    public void disable(long id) {
        jdbc.update("UPDATE push_device SET enabled = FALSE, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ?", id);
    }

    public List<PushDevice> findEnabledByUser(String userId) {
        return jdbc.query("""
                SELECT id, user_id, platform, firebase_registration_id, app_instance_id
                  FROM push_device WHERE user_id = ? AND enabled = TRUE ORDER BY id
                """, this::map, userId);
    }

    public long countDisabled() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM push_device WHERE enabled = FALSE", Long.class);
        return count == null ? 0 : count;
    }

    private PushDevice map(ResultSet rs, int rowNum) throws SQLException {
        return new PushDevice(rs.getLong("id"), rs.getString("user_id"),
                Platform.valueOf(rs.getString("platform")), rs.getString("firebase_registration_id"),
                rs.getString("app_instance_id"));
    }
}
