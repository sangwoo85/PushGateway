package com.sangwoo.push.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sangwoo.push.domain.NotificationType;
import com.sangwoo.push.repository.PushCompletionRepository;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PushHistoryControllerTest {
    private final PushCompletionRepository repository = mock(PushCompletionRepository.class);
    private final PushHistoryController controller = new PushHistoryController(repository);

    @Test
    void returnsOnlyAuthenticatedUsersHistory() {
        UUID eventId = UUID.randomUUID();
        Instant sentAt = Instant.parse("2026-09-03T01:02:03Z");
        when(repository.findRecentForUser("user-1", 50)).thenReturn(List.of(
                new PushCompletionRepository.PushHistoryEntry(
                        eventId, NotificationType.TASK_ARRIVED, null, sentAt)));

        var result = controller.history(50, () -> "user-1");

        assertThat(result).containsExactly(new PushHistoryController.PushHistoryResponse(
                eventId, NotificationType.TASK_ARRIVED, null, sentAt));
        verify(repository).findRecentForUser("user-1", 50);
    }

    @Test
    void capsRequestedHistorySize() {
        Principal principal = () -> "user-1";
        controller.history(1000, principal);
        verify(repository).findRecentForUser("user-1", 100);
    }

    @Test
    void rejectsUnauthenticatedHistoryRequest() {
        assertThatThrownBy(() -> controller.history(50, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }
}
