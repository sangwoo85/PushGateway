package com.example.pushgateway.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.pushgateway.domain.NotificationType;
import com.example.pushgateway.domain.PushTargetType;
import com.example.pushgateway.repository.PushQueueRepository;
import com.example.pushgateway.template.NotificationTemplateFactory;
import org.junit.jupiter.api.Test;

class PushQueueServiceTest {
    private final PushQueueRepository repository = mock(PushQueueRepository.class);
    private final PushQueueService service = new PushQueueService(repository, new NotificationTemplateFactory());

    @Test
    void enqueuesTargetWithoutAcceptingTopic() {
        service.enqueueUser("user-1", NotificationType.TASK_ARRIVED, null);
        verify(repository).insert(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(PushTargetType.USER),
                org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.eq(NotificationType.TASK_ARRIVED),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void actorIsRequiredOnlyForMentionTypes() {
        assertThatThrownBy(() -> service.enqueueUser("u", NotificationType.TASK_MENTIONED, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.enqueueUser("u", NotificationType.TASK_ARRIVED, "홍길동"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
