package com.sangwoo.push.domain;

import java.time.Instant;
import java.util.UUID;

public record PushQueueItem(long id, UUID eventId, PushTargetType targetType, String targetId,
                            NotificationType notificationType, String actorName,
                            QueueStatus status, int attemptCount, Instant createdAt,
                            String lockedBy) {}
