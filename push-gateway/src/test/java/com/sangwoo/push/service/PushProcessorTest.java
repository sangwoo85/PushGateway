package com.sangwoo.push.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sangwoo.push.config.MetricsConfiguration.PushCounters;
import com.sangwoo.push.config.PushProperties;
import com.sangwoo.push.domain.NotificationType;
import com.sangwoo.push.domain.Platform;
import com.sangwoo.push.domain.PushQueueItem;
import com.sangwoo.push.domain.QueueStatus;
import com.sangwoo.push.domain.PushTargetType;
import com.sangwoo.push.repository.PushCompletionRepository;
import com.sangwoo.push.repository.PushQueueRepository;
import com.sangwoo.push.sender.PushSender;
import com.sangwoo.push.sender.PushSender.Outcome;
import com.sangwoo.push.sender.PushSender.SendResult;
import com.sangwoo.push.template.NotificationTemplateFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PushProcessorTest {
    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private final PushQueueRepository queue = mock(PushQueueRepository.class);
    private final PushTopicService topics = mock(PushTopicService.class);
    private final PushCompletionRepository completion = mock(PushCompletionRepository.class);
    private final PushSender sender = mock(PushSender.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private PushProcessor processor;
    private PushQueueItem item;

    @BeforeEach
    void setUp() {
        var counters = new PushCounters(meterRegistry.counter("success"), meterRegistry.counter("retry"),
                meterRegistry.counter("dead"), meterRegistry.counter("topic-failure"), meterRegistry);
        var properties = new PushProperties(
                new PushProperties.Scheduler(true, Duration.ofSeconds(30), 100, Duration.ofMinutes(5), 5),
                new PushProperties.Firebase(false, ""));
        processor = new PushProcessor(queue, topics, completion, new NotificationTemplateFactory(), sender,
                new RetryPolicy(), properties, counters, Clock.fixed(NOW, ZoneOffset.UTC));
        item = new PushQueueItem(1L, UUID.randomUUID(), PushTargetType.USER, "user-1", NotificationType.TASK_ARRIVED,
                null, QueueStatus.PROCESSING, 0, NOW.minusSeconds(10), "worker");
        when(queue.claimBatch(eq("worker"), any(), any(), eq(100))).thenReturn(List.of(item));
        when(topics.resolve(PushTargetType.USER, "user-1")).thenReturn(java.util.Optional.of("usr_secret-topic"));
    }

    @Test
    void acceptedResponseCompletesHistoryAndDelete() {
        when(sender.send(eq(item), eq("usr_secret-topic"), any())).thenReturn(SendResult.accepted("message-1"));
        processor.processBatch("worker");
        verify(completion).completeAccepted(eq(item), eq(Platform.IOS), eq("message-1"),
                eq("FCM_ACCEPTED"), eq(NOW), eq(NOW));
        verify(queue, never()).markRetry(any(), anyInt(), any(), any());
    }

    @Test
    void transientFailureKeepsQueueForRetry() {
        when(sender.send(eq(item), eq("usr_secret-topic"), any()))
                .thenReturn(SendResult.failure(Outcome.TRANSIENT_FAILURE, "FCM_UNAVAILABLE"));
        processor.processBatch("worker");
        verify(queue).markRetry(item, 1, NOW.plusSeconds(30), "FCM_UNAVAILABLE");
        verify(completion, never()).completeAccepted(any(), any(), any(), any(), any(), any());
    }

    @Test
    void permanentTopicFailureMovesDead() {
        when(sender.send(eq(item), eq("usr_secret-topic"), any()))
                .thenReturn(SendResult.failure(Outcome.PERMANENT_FAILURE, "FCM_INVALID_ARGUMENT"));
        processor.processBatch("worker");
        verify(queue).markDead(item, 1, "FCM_INVALID_ARGUMENT");
    }

    @Test
    void authFailureDoesNotRetry() {
        when(sender.send(eq(item), eq("usr_secret-topic"), any()))
                .thenReturn(SendResult.failure(Outcome.AUTH_FAILURE, "FCM_THIRD_PARTY_AUTH_ERROR"));
        processor.processBatch("worker");
        verify(queue).markDead(item, 1, "FCM_THIRD_PARTY_AUTH_ERROR");
    }

    @Test
    void missingTopicMovesDeadWithoutSending() {
        when(topics.resolve(PushTargetType.USER, "user-1")).thenReturn(java.util.Optional.empty());
        processor.processBatch("worker");
        verify(queue).markDead(item, 1, "TOPIC_BINDING_MISSING_OR_DISABLED");
        verify(sender, never()).send(any(), any(), any());
    }
}
