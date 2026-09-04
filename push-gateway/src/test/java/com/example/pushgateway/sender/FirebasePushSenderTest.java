package com.example.pushgateway.sender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.pushgateway.domain.NotificationType;
import com.example.pushgateway.domain.PushQueueItem;
import com.example.pushgateway.domain.PushTargetType;
import com.example.pushgateway.domain.QueueStatus;
import com.example.pushgateway.template.NotificationTemplateFactory.NotificationContent;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FirebasePushSenderTest {
    @Test
    void buildsAndroidDataOnlyAndIosAlertWithWireKeys() throws Exception {
        var registry = new SimpleMeterRegistry();
        var sender = new FirebasePushSender(mock(FirebaseMessaging.class), registry.timer("latency"));
        var item = new PushQueueItem(1, UUID.randomUUID(), PushTargetType.USER, "u",
                NotificationType.TASK_COMMENT_CREATED, null, QueueStatus.PROCESSING, 0,
                Instant.now(), "w");
        Message message = sender.buildMessage(item, "usr_opaque", new NotificationContent("업무 알림", "내용"));
        assertThat(field(message, "topic")).isEqualTo("usr_opaque");
        assertThat(field(message, "token")).isNull();
        assertThat(field(message, "notification")).isNull();
        assertThat(field(message, "androidConfig")).isNotNull();
        assertThat(field(message, "apnsConfig")).isNotNull();
        @SuppressWarnings("unchecked") Map<String, String> data = (Map<String, String>) field(message, "data");
        assertThat(data).containsEntry("notificationType", "COMMENT_ADDED")
                .containsEntry("eventId", item.eventId().toString())
                .doesNotContainKey("type");
    }

    private Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
