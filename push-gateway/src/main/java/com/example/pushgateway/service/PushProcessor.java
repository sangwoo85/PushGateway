package com.example.pushgateway.service;

import com.example.pushgateway.config.MetricsConfiguration.PushCounters;
import com.example.pushgateway.config.PushProperties;
import com.example.pushgateway.domain.Platform;
import com.example.pushgateway.domain.PushQueueItem;
import com.example.pushgateway.repository.PushCompletionRepository;
import com.example.pushgateway.repository.PushQueueRepository;
import com.example.pushgateway.sender.PushSender;
import com.example.pushgateway.sender.PushSender.Outcome;
import com.example.pushgateway.sender.PushSender.SendResult;
import com.example.pushgateway.template.NotificationTemplateFactory;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PushProcessor {
    private static final Logger log = LoggerFactory.getLogger(PushProcessor.class);
    private final PushQueueRepository queue;
    private final PushTopicService topics;
    private final PushCompletionRepository completion;
    private final NotificationTemplateFactory templates;
    private final PushSender sender;
    private final RetryPolicy retryPolicy;
    private final PushProperties properties;
    private final PushCounters counters;
    private final Clock clock;

    @Autowired
    public PushProcessor(PushQueueRepository queue, PushTopicService topics,
                         PushCompletionRepository completion, NotificationTemplateFactory templates,
                         PushSender sender, RetryPolicy retryPolicy, PushProperties properties,
                         PushCounters counters) {
        this(queue, topics, completion, templates, sender, retryPolicy, properties, counters, Clock.systemUTC());
    }

    PushProcessor(PushQueueRepository queue, PushTopicService topics,
                  PushCompletionRepository completion, NotificationTemplateFactory templates,
                  PushSender sender, RetryPolicy retryPolicy, PushProperties properties,
                  PushCounters counters, Clock clock) {
        this.queue = queue;
        this.topics = topics;
        this.completion = completion;
        this.templates = templates;
        this.sender = sender;
        this.retryPolicy = retryPolicy;
        this.properties = properties;
        this.counters = counters;
        this.clock = clock;
    }

    public int processBatch(String workerId) {
        Instant now = clock.instant();
        List<PushQueueItem> claimed = queue.claimBatch(workerId, now,
                now.minus(properties.scheduler().processingTimeout()), properties.scheduler().batchSize());
        claimed.forEach(this::processOneSafely);
        return claimed.size();
    }

    private void processOneSafely(PushQueueItem item) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("eventId", item.eventId().toString())) {
            processOne(item);
        } catch (RuntimeException ex) {
            log.error("Push processing failed unexpectedly; queue retained", ex);
            fail(item, "PROCESSOR_UNEXPECTED", true);
        }
    }

    private void processOne(PushQueueItem item) {
        var topic = topics.resolve(item.targetType(), item.targetId());
        if (topic.isEmpty()) {
            counters.topicResolutionFailure().increment();
            fail(item, "TOPIC_BINDING_MISSING_OR_DISABLED", false);
            return;
        }

        Instant requestedAt = clock.instant();
        var content = templates.create(item.notificationType(), item.actorName());
        counters.fcmRequest(item.targetType()).increment();
        SendResult result = sender.send(item, topic.get(), content);
        if (result.outcome() == Outcome.ACCEPTED) {
            completion.completeAccepted(item, Platform.IOS, result.messageId(), "FCM_ACCEPTED",
                    requestedAt, clock.instant());
            counters.success().increment();
            log.info("Push accepted by FCM; this does not prove device display");
        } else {
            fail(item, safeCode(result.errorCode()), result.outcome() == Outcome.TRANSIENT_FAILURE);
        }
    }

    private void fail(PushQueueItem item, String errorCode, boolean retryable) {
        RetryPolicy.RetryDecision decision = retryPolicy.afterFailure(
                item.attemptCount(), properties.scheduler().maxAttempts(), retryable);
        if (decision.dead()) {
            queue.markDead(item, decision.attemptCount(), safeCode(errorCode));
            counters.dead().increment();
            log.warn("Push moved to DEAD with errorCode={}", safeCode(errorCode));
        } else {
            queue.markRetry(item, decision.attemptCount(),
                    clock.instant().plus(decision.delay()), safeCode(errorCode));
            counters.retry().increment();
            log.warn("Push scheduled for retry with errorCode={}", safeCode(errorCode));
        }
    }

    private String safeCode(String code) {
        if (code == null || code.isBlank()) return "UNKNOWN";
        return code.length() <= 64 ? code : code.substring(0, 64);
    }
}
