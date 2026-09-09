package com.sangwoo.push.service;

import com.sangwoo.push.domain.NotificationType;
import com.sangwoo.push.domain.PushTargetType;
import com.sangwoo.push.repository.PushQueueRepository;
import com.sangwoo.push.template.NotificationTemplateFactory;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushQueueService {
    private final PushQueueRepository repository;
    private final NotificationTemplateFactory templates;
    private final PushTargetPolicy targetPolicy;

    @Autowired
    public PushQueueService(PushQueueRepository repository, NotificationTemplateFactory templates,
                            PushTargetPolicy targetPolicy) {
        this.repository = repository;
        this.templates = templates;
        this.targetPolicy = targetPolicy;
    }

    public PushQueueService(PushQueueRepository repository, NotificationTemplateFactory templates) {
        this(repository, templates, new PushTargetPolicy());
    }

    /** 기존 업무의 @Transactional 메서드 안에서 호출하면 같은 DB 트랜잭션에 참여한다. */
    @Transactional
    public UUID enqueue(UUID eventId, PushTargetType targetType, String targetId,
                        NotificationType type, String actorName) {
        targetPolicy.validate(targetType, type);
        return enqueueValidated(eventId, targetType, targetId, type, actorName);
    }

    /** test-page Bean/권한이 활성화된 관리자 화면에서만 호출한다. */
    @Transactional
    public UUID enqueueForTest(PushTargetType targetType, String targetId,
                               NotificationType type, String actorName) {
        return enqueueValidated(UUID.randomUUID(), targetType, targetId, type, actorName);
    }

    private UUID enqueueValidated(UUID eventId, PushTargetType targetType, String targetId,
                                  NotificationType type, String actorName) {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(type, "notificationType is required");
        String normalizedTarget = PushTopicService.normalizedTarget(targetType, targetId);
        String normalizedActor = templates.normalizeActor(actorName);
        if (!type.actorRequired() && normalizedActor != null) {
            throw new IllegalArgumentException("actorName is not allowed for " + type);
        }
        templates.create(type, normalizedActor);
        repository.insert(eventId, targetType, normalizedTarget, type, normalizedActor);
        return eventId;
    }

    @Transactional
    public UUID enqueue(PushTargetType targetType, String targetId, NotificationType type, String actorName) {
        return enqueue(UUID.randomUUID(), targetType, targetId, type, actorName);
    }

    public UUID enqueueUser(String userId, NotificationType type, String actorName) {
        return enqueue(PushTargetType.USER, userId, type, actorName);
    }

    public UUID enqueueDepartment(String departmentId, NotificationType type, String actorName) {
        return enqueue(PushTargetType.DEPARTMENT, departmentId, type, actorName);
    }

    public UUID enqueueNotice(NotificationType type, String actorName) {
        return enqueue(PushTargetType.NOTICE, PushTopicService.NOTICE_TARGET_ID, type, actorName);
    }

    /** 호환 기간 동안 기존 업무 코드는 사용자 발송으로 동작한다. */
    @Deprecated(forRemoval = true)
    public UUID enqueue(UUID eventId, String recipientUserId, NotificationType type, String actorName) {
        return enqueue(eventId, PushTargetType.USER, recipientUserId, type, actorName);
    }

    @Deprecated(forRemoval = true)
    public UUID enqueue(String recipientUserId, NotificationType type, String actorName) {
        return enqueueUser(recipientUserId, type, actorName);
    }
}
