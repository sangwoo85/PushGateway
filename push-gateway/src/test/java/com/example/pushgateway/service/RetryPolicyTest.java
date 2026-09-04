package com.example.pushgateway.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {
    private final RetryPolicy policy = new RetryPolicy();

    @Test
    void usesConfiguredBackoffAndThenDead() {
        assertThat(policy.afterFailure(0, 5, true).delay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.afterFailure(1, 5, true).delay()).isEqualTo(Duration.ofMinutes(2));
        assertThat(policy.afterFailure(2, 5, true).delay()).isEqualTo(Duration.ofMinutes(5));
        assertThat(policy.afterFailure(3, 5, true).delay()).isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.afterFailure(4, 5, true).dead()).isTrue();
    }

    @Test
    void permanentFailureDiesImmediately() {
        var decision = policy.afterFailure(0, 5, false);
        assertThat(decision.dead()).isTrue();
        assertThat(decision.attemptCount()).isOne();
    }
}

