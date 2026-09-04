package com.example.pushgateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.pushgateway.domain.NotificationType;
import com.example.pushgateway.domain.Platform;
import com.example.pushgateway.domain.QueueStatus;
import com.example.pushgateway.domain.PushTargetType;
import com.example.pushgateway.repository.PushCompletionRepository;
import com.example.pushgateway.repository.PushQueueRepository;
import com.example.pushgateway.service.PushQueueService;
import com.example.pushgateway.service.PushTopicService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"push.scheduler.enabled=false", "push.firebase.enabled=false"})
class MariaDbIntegrationTest {
    @Container
    static final MariaDBContainer<?> MARIA_DB = new MariaDBContainer<>("mariadb:11.4")
            .withDatabaseName("push_test");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIA_DB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIA_DB::getUsername);
        registry.add("spring.datasource.password", MARIA_DB::getPassword);
    }

    @Autowired PushQueueService service;
    @Autowired PushQueueRepository queue;
    @Autowired PushCompletionRepository completion;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;
    @Autowired PushTopicService topics;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM push_send_history");
        jdbc.update("DELETE FROM push_device");
        jdbc.update("DELETE FROM push_queue");
        jdbc.update("DELETE FROM push_topic_binding WHERE target_type <> 'NOTICE'");
        jdbc.execute("CREATE TABLE IF NOT EXISTS business_stub (id BIGINT PRIMARY KEY, value_text VARCHAR(20)) ENGINE=InnoDB");
        jdbc.update("DELETE FROM business_stub");
    }

    @Test
    void businessWriteAndQueueInsertRollbackTogether() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO business_stub (id, value_text) VALUES (1, 'created')");
            service.enqueue(UUID.randomUUID(), "user-1", NotificationType.TASK_ARRIVED, null);
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_stub", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM push_queue", Integer.class)).isZero();
    }

    @Test
    void secondWorkerCannotClaimTheSameItem() {
        service.enqueue(UUID.randomUUID(), "user-1", NotificationType.TASK_ARRIVED, null);
        Instant now = Instant.now().plusSeconds(1);
        var first = queue.claimBatch("worker-1", now, now.minusSeconds(300), 100);
        var second = queue.claimBatch("worker-2", now, now.minusSeconds(300), 100);
        assertThat(first).hasSize(1);
        assertThat(second).isEmpty();
    }

    @Test
    void successHistoryAndQueueDeleteAreAtomic() {
        UUID eventId = service.enqueue("user-1", NotificationType.NOTICE_CREATED, null);
        Instant now = Instant.now().plusSeconds(1);
        var item = queue.claimBatch("worker", now, now.minusSeconds(300), 1).getFirst();
        completion.completeAccepted(item, Platform.IOS, "fcm-message", "FCM_ACCEPTED", now, now);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM push_queue WHERE event_id = ?", Integer.class,
                eventId.toString())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM push_send_history WHERE event_id = ?", Integer.class,
                eventId.toString())).isOne();
        assertThat(completion.findRecentForUser("user-1", 10))
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.eventId()).isEqualTo(eventId);
                    assertThat(history.notificationType()).isEqualTo(NotificationType.NOTICE_CREATED);
                    assertThat(history.sentAt()).isNotNull();
                });
    }

    @Test
    void historyFailureRollsBackQueueDelete() {
        UUID eventId = service.enqueue("user-1", NotificationType.NOTICE_CREATED, null);
        Instant now = Instant.now().plusSeconds(1);
        var item = queue.claimBatch("worker", now, now.minusSeconds(300), 1).getFirst();
        jdbc.update("""
                INSERT INTO push_send_history
                (event_id, target_type, target_id, delivery_channel, recipient_user_id,
                 notification_type, platform, result_code, requested_at, sent_at)
                VALUES (?, 'USER', 'user-1', 'TOPIC', 'user-1', 'NOTICE_CREATED',
                        'IOS', 'PREEXISTING', ?, ?)
                """, eventId.toString(), now, now);
        assertThatThrownBy(() -> completion.completeAccepted(
                item, Platform.IOS, "message", "FCM_ACCEPTED", now, now)).isInstanceOf(Exception.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM push_queue WHERE event_id = ?", Integer.class,
                eventId.toString())).isOne();
    }

    @Test
    void staleProcessingIsRecoveredAndMaximumAttemptCanBeDead() {
        service.enqueue(UUID.randomUUID(), "user-1", NotificationType.TASK_ARRIVED, null);
        Instant first = Instant.now().plusSeconds(1);
        var item = queue.claimBatch("crashed", first, first.minusSeconds(300), 1).getFirst();
        jdbc.update("UPDATE push_queue SET locked_at = ? WHERE id = ?", first.minusSeconds(600), item.id());
        var recovered = queue.claimBatch("recovery", first.plusSeconds(1), first.minusSeconds(300), 1);
        assertThat(recovered).hasSize(1);
        assertThatThrownBy(() -> completion.completeAccepted(
                item, Platform.IOS, "late-message", "FCM_ACCEPTED", first, first.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        queue.markDead(recovered.getFirst(), 5, "MAX_ATTEMPTS");
        assertThat(queue.countByStatus(QueueStatus.DEAD)).isOne();
    }

    @Test
    void databaseRejectsUnknownNotificationType() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO push_queue
                    (event_id, recipient_user_id, notification_type, status, next_attempt_at)
                VALUES (?, 'user-1', 'ARBITRARY_MESSAGE', 'PENDING', CURRENT_TIMESTAMP(6))
                """, UUID.randomUUID().toString())).isInstanceOf(Exception.class);
    }

    @Test
    void topicBindingIsUniqueAndOpaqueForAllTargetKinds() {
        service.enqueueUser("user-1", NotificationType.TASK_ARRIVED, null);
        service.enqueueDepartment("dept-1", NotificationType.SOURCE_OVERLAP, null);
        service.enqueueNotice(NotificationType.NOTICE_CREATED, null);
        var claimed = queue.claimBatch("target-worker", Instant.now().plusSeconds(1),
                Instant.now().minusSeconds(300), 10);
        assertThat(claimed).extracting(item -> item.targetType())
                .containsExactlyInAnyOrder(PushTargetType.USER, PushTargetType.DEPARTMENT, PushTargetType.NOTICE);

        jdbc.update("""
                INSERT INTO push_topic_binding(target_type,target_id,topic_name,enabled)
                VALUES ('USER','employee-secret','usr_01234567890123456789012345678901',TRUE)
                """);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO push_topic_binding(target_type,target_id,topic_name,enabled)
                VALUES ('USER','employee-secret','usr_abcdefghijklmnopqrstuvwxyz123456',TRUE)
                """)).isInstanceOf(Exception.class);
        String topic = jdbc.queryForObject("""
                SELECT topic_name FROM push_topic_binding
                 WHERE target_type='USER' AND target_id='employee-secret'
                """, String.class);
        assertThat(topic).doesNotContain("employee-secret");
    }

    @Test
    void targetHistoryIndexSupportsBoundedLargeHistoryLookup() {
        jdbc.batchUpdate("""
                INSERT INTO push_send_history
                    (event_id,target_type,target_id,delivery_channel,recipient_user_id,
                     notification_type,platform,result_code,requested_at,sent_at)
                VALUES (?, 'USER', 'user-1', 'TOPIC', 'user-1', 'TASK_ARRIVED',
                        'IOS', 'FCM_ACCEPTED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, java.util.stream.IntStream.range(0, 3001).boxed().toList(), 250,
                (statement, value) -> statement.setString(1, UUID.randomUUID().toString()));
        String planKey = jdbc.queryForObject("""
                EXPLAIN SELECT event_id FROM push_send_history
                 WHERE target_type='USER' AND target_id='user-1'
                 ORDER BY sent_at DESC, id DESC LIMIT 100
                """, (rs, row) -> rs.getString("key"));
        assertThat(planKey).isEqualTo("ix_push_history_target_sent");
        Integer bounded = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT id FROM push_send_history WHERE target_type='USER' AND target_id='user-1'
                    ORDER BY sent_at DESC, id DESC LIMIT 100
                ) recent
                """, Integer.class);
        assertThat(bounded).isEqualTo(100);
    }

    @Test
    void existingV1UserRowsAreBackfilledByV2() {
        String schema = "legacy_backfill";
        String adminUrl = MARIA_DB.getJdbcUrl().replace("/push_test", "/mysql");
        var admin = new JdbcTemplate(new DriverManagerDataSource(adminUrl, "root", MARIA_DB.getPassword()));
        admin.execute("DROP DATABASE IF EXISTS " + schema);
        admin.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
        String url = MARIA_DB.getJdbcUrl().replace("/push_test", "/" + schema);
        Flyway.configure().dataSource(url, "root", MARIA_DB.getPassword())
                .locations("classpath:db/migration").target("1").load().migrate();
        var legacy = new JdbcTemplate(new DriverManagerDataSource(
                url, "root", MARIA_DB.getPassword()));
        UUID event = UUID.randomUUID();
        legacy.update("""
                INSERT INTO push_queue(event_id,recipient_user_id,notification_type,status,next_attempt_at)
                VALUES (?, 'legacy-user', 'TASK_ARRIVED', 'PENDING', CURRENT_TIMESTAMP(6))
                """, event.toString());
        Flyway.configure().dataSource(url, "root", MARIA_DB.getPassword())
                .locations("classpath:db/migration").load().migrate();
        var target = legacy.queryForMap("SELECT target_type,target_id,delivery_channel FROM push_queue WHERE event_id=?",
                event.toString());
        assertThat(target).containsEntry("target_type", "USER")
                .containsEntry("target_id", "legacy-user")
                .containsEntry("delivery_channel", "TOPIC");
    }

    @Test
    void concurrentTopicCreationKeepsOneBinding() throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                ready.countDown(); start.await();
                return topics.getOrCreate(PushTargetType.USER, "concurrent-user");
            });
            var second = executor.submit(() -> {
                ready.countDown(); start.await();
                return topics.getOrCreate(PushTargetType.USER, "concurrent-user");
            });
            ready.await();
            start.countDown();
            assertThat(first.get()).isEqualTo(second.get());
        }
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM push_topic_binding
                 WHERE target_type='USER' AND target_id='concurrent-user'
                """, Integer.class)).isOne();
    }
}
