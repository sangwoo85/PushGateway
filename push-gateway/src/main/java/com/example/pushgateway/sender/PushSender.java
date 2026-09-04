package com.example.pushgateway.sender;

import com.example.pushgateway.domain.PushQueueItem;
import com.example.pushgateway.template.NotificationTemplateFactory.NotificationContent;

public interface PushSender {
    SendResult send(PushQueueItem item, String topic, NotificationContent notification);

    enum Outcome { ACCEPTED, TRANSIENT_FAILURE, PERMANENT_FAILURE, AUTH_FAILURE }

    record SendResult(Outcome outcome, String messageId, String errorCode) {
        public static SendResult accepted(String messageId) {
            return new SendResult(Outcome.ACCEPTED, messageId, "FCM_ACCEPTED");
        }

        public static SendResult failure(Outcome outcome, String errorCode) {
            return new SendResult(outcome, null, errorCode);
        }
    }
}
