package com.sangwoo.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sangwoo.push.domain.PushTargetType;
import com.sangwoo.push.repository.PushTopicBindingRepository;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PushTopicServiceTest {
    @Test
    void createsOpaqueUserAndDepartmentTopics() {
        assertOpaque(PushTargetType.USER, "employee-123456", "usr_");
        assertOpaque(PushTargetType.DEPARTMENT, "finance-secret", "dept_");
    }

    @Test
    void resolvesNoticeToFixedTopic() {
        PushTopicBindingRepository repository = mock(PushTopicBindingRepository.class);
        when(repository.findEnabled(PushTargetType.NOTICE, "ALL")).thenReturn(Optional.of("notice_all"));
        assertThat(new PushTopicService(repository).getOrCreate(PushTargetType.NOTICE, null))
                .isEqualTo("notice_all");
    }

    private void assertOpaque(PushTargetType type, String id, String prefix) {
        PushTopicBindingRepository repository = mock(PushTopicBindingRepository.class);
        AtomicReference<String> stored = new AtomicReference<>();
        when(repository.findEnabled(type, id)).thenAnswer(ignored -> Optional.ofNullable(stored.get()));
        when(repository.findEnabledForUpdate(type, id)).thenAnswer(ignored -> Optional.ofNullable(stored.get()));
        doAnswer(invocation -> { stored.set(invocation.getArgument(2)); return null; })
                .when(repository).insert(org.mockito.ArgumentMatchers.eq(type),
                        org.mockito.ArgumentMatchers.eq(id), anyString());
        String topic = new PushTopicService(repository, new SecureRandom()).getOrCreate(type, id);
        assertThat(topic).startsWith(prefix).hasSizeGreaterThanOrEqualTo(prefix.length() + 32);
        assertThat(topic).doesNotContain(id);
    }
}
