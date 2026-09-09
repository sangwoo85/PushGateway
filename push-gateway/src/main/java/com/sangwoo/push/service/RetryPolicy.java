package com.sangwoo.push.service;

import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicy {
    private static final List<Duration> DELAYS = List.of(
            Duration.ofSeconds(30), Duration.ofMinutes(2),
            Duration.ofMinutes(5), Duration.ofMinutes(15));

    public RetryDecision afterFailure(int previousAttempts, int maxAttempts, boolean retryable) {
        int attempts = previousAttempts + 1;
        if (!retryable || attempts >= maxAttempts) {
            return new RetryDecision(attempts, true, Duration.ZERO);
        }
        int index = Math.min(attempts - 1, DELAYS.size() - 1);
        return new RetryDecision(attempts, false, DELAYS.get(index));
    }

    public record RetryDecision(int attemptCount, boolean dead, Duration delay) {}
}
