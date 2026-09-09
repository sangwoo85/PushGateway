package com.sangwoo.push.sender;

import com.sangwoo.push.domain.PushQueueItem;
import com.sangwoo.push.template.NotificationTemplateFactory;
import com.sangwoo.push.template.NotificationTemplateFactory.NotificationContent;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "push.firebase", name = "enabled", havingValue = "true")
public class FirebasePushSender implements PushSender {
    private final FirebaseMessaging messaging;
    private final Timer latency;

    public FirebasePushSender(FirebaseMessaging messaging, Timer pushFcmLatency) {
        this.messaging = messaging;
        this.latency = pushFcmLatency;
    }

    @Override
    @SuppressWarnings("deprecation")
    public SendResult send(PushQueueItem item, String topic, NotificationContent content) {
        Message message = buildMessage(item, topic, content);
        try {
            return SendResult.accepted(latency.recordCallable(() -> messaging.send(message)));
        } catch (FirebaseMessagingException ex) {
            return classify(ex);
        } catch (Exception ex) {
            return SendResult.failure(Outcome.TRANSIENT_FAILURE, "FCM_UNEXPECTED");
        }
    }

    Message buildMessage(PushQueueItem item, String topic, NotificationContent content) {
        return Message.builder()
                .setTopic(topic)
                .putData("eventId", item.eventId().toString())
                .putData("notificationType", item.notificationType().wireName())
                .putData("title", content.title())
                .putData("body", content.body())
                .putData("templateVersion", NotificationTemplateFactory.TEMPLATE_VERSION)
                // Android must receive data-only messages so FirebaseMessagingService can
                // persist every event before showing the local notification.
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .putHeader("apns-priority", "10")
                        .setAps(Aps.builder()
                                .setAlert(ApsAlert.builder()
                                        .setTitle(content.title())
                                        .setBody(content.body())
                                        .build())
                                .setSound("default")
                                .build())
                .build())
                .build();
    }

    private SendResult classify(FirebaseMessagingException ex) {
        MessagingErrorCode code = ex.getMessagingErrorCode();
        String errorCode = code == null ? "FCM_UNKNOWN" : "FCM_" + code.name();
        if (code == null) return SendResult.failure(Outcome.TRANSIENT_FAILURE, errorCode);
        return switch (code) {
            case UNREGISTERED, SENDER_ID_MISMATCH, INVALID_ARGUMENT ->
                    SendResult.failure(Outcome.PERMANENT_FAILURE, errorCode);
            case THIRD_PARTY_AUTH_ERROR -> SendResult.failure(Outcome.AUTH_FAILURE, errorCode);
            case UNAVAILABLE, INTERNAL, QUOTA_EXCEEDED -> SendResult.failure(Outcome.TRANSIENT_FAILURE, errorCode);
        };
    }
}
