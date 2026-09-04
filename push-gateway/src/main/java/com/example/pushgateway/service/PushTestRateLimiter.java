package com.example.pushgateway.service;

import com.example.pushgateway.config.PushProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class PushTestRateLimiter {
    private final Map<String, ArrayDeque<Instant>> attempts = new ConcurrentHashMap<>();
    private final PushProperties properties;
    private final Clock clock = Clock.systemUTC();

    public PushTestRateLimiter(PushProperties properties) { this.properties = properties; }

    public void check(String principal) {
        ArrayDeque<Instant> values = attempts.computeIfAbsent(principal, ignored -> new ArrayDeque<>());
        synchronized (values) {
            Instant cutoff = clock.instant().minusSeconds(60);
            while (!values.isEmpty() && values.peekFirst().isBefore(cutoff)) values.removeFirst();
            if (values.size() >= properties.testPage().requestsPerMinute()) {
                throw new IllegalStateException("1분 발송 제한을 초과했습니다.");
            }
            values.addLast(clock.instant());
        }
    }
}
