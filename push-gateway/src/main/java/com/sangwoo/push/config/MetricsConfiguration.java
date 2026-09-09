package com.sangwoo.push.config;

import com.sangwoo.push.repository.PushQueueRepository;
import com.sangwoo.push.domain.PushTargetType;
import com.sangwoo.push.domain.QueueStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MetricsConfiguration {
    @Bean
    Timer pushFcmLatency(MeterRegistry registry) {
        return Timer.builder("push.fcm.request").description("FCM request latency").register(registry);
    }

    @Bean
    PushCounters pushCounters(MeterRegistry registry, PushQueueRepository queue) {
        Gauge.builder("push.queue.waiting", queue, PushQueueRepository::countWaiting)
                .description("PENDING and RETRY queue count").register(registry);
        Gauge.builder("push.queue.dead", queue, value -> value.countByStatus(QueueStatus.DEAD))
                .description("DEAD queue count").register(registry);
        for (PushTargetType type : PushTargetType.values()) {
            Gauge.builder("push.queue.waiting.by_target", queue, value -> value.countWaiting(type))
                    .tag("target_type", type.name()).register(registry);
        }
        return new PushCounters(
                Counter.builder("push.process.success").register(registry),
                Counter.builder("push.process.retry").register(registry),
                Counter.builder("push.process.dead").register(registry),
                Counter.builder("push.topic.resolution.failure").register(registry),
                registry);
    }

    public record PushCounters(Counter success, Counter retry, Counter dead,
                               Counter topicResolutionFailure, MeterRegistry registry) {
        public Counter fcmRequest(PushTargetType type) {
            return registry.counter("push.fcm.requests", "target_type", type.name());
        }
    }
}
