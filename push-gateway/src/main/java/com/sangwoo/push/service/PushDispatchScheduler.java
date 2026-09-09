package com.sangwoo.push.service;

import java.lang.management.ManagementFactory;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "push.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "push.firebase", name = "enabled", havingValue = "true")
public class PushDispatchScheduler {
    private final PushProcessor processor;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName() + "-" + UUID.randomUUID();

    public PushDispatchScheduler(PushProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${push.scheduler.fixed-delay:30s}")
    public void dispatch() {
        processor.processBatch(workerId);
    }
}

