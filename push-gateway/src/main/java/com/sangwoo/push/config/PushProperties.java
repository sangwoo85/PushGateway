package com.sangwoo.push.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties("push")
public record PushProperties(Scheduler scheduler, Firebase firebase, Qr qr, TestPage testPage) {
    public PushProperties(Scheduler scheduler, Firebase firebase) {
        this(scheduler, firebase, null, null);
    }

    @ConstructorBinding
    public PushProperties {
        scheduler = scheduler == null ? new Scheduler(true, Duration.ofSeconds(30), 100, Duration.ofMinutes(5), 5) : scheduler;
        firebase = firebase == null ? new Firebase(false, "") : firebase;
        qr = qr == null ? new Qr("", Duration.ofMinutes(3)) : qr;
        testPage = testPage == null ? new TestPage(false, true, "", "", true, 10) : testPage;
    }

    public record Scheduler(boolean enabled, Duration fixedDelay, int batchSize,
                            Duration processingTimeout, int maxAttempts) {
        public Scheduler {
            fixedDelay = fixedDelay == null ? Duration.ofSeconds(30) : fixedDelay;
            processingTimeout = processingTimeout == null ? Duration.ofMinutes(5) : processingTimeout;
            if (batchSize < 1) batchSize = 100;
            if (maxAttempts < 1) maxAttempts = 5;
        }
    }

    public record Firebase(boolean enabled, String projectId) {
        public Firebase {
            projectId = projectId == null ? "" : projectId.trim();
        }
    }

    public record Qr(String privateKeyPath, Duration ttl) {
        public Qr {
            privateKeyPath = privateKeyPath == null ? "" : privateKeyPath.trim();
            ttl = ttl == null ? Duration.ofMinutes(3) : ttl;
            if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofMinutes(10)) > 0) {
                throw new IllegalArgumentException("push.qr.ttl must be between 1ns and 10m");
            }
        }
    }

    public record TestPage(boolean enabled, boolean authenticationEnabled, String username, String passwordHash,
                           boolean enqueueWhenFirebaseDisabled, int requestsPerMinute) {
        public TestPage {
            username = username == null ? "" : username.trim();
            passwordHash = passwordHash == null ? "" : passwordHash.trim();
            if (requestsPerMinute < 1) requestsPerMinute = 10;
        }
    }
}
