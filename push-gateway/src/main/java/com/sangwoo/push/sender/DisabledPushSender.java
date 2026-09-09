package com.sangwoo.push.sender;

import com.sangwoo.push.domain.PushQueueItem;
import com.sangwoo.push.template.NotificationTemplateFactory.NotificationContent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "push.firebase", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledPushSender implements PushSender {
    @Override
    public SendResult send(PushQueueItem item, String topic, NotificationContent notification) {
        return SendResult.failure(Outcome.AUTH_FAILURE, "FCM_DISABLED");
    }
}
