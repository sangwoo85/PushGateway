package com.example.pushgateway.template;

import com.example.pushgateway.domain.NotificationType;
import java.text.Normalizer;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class NotificationTemplateFactory {
    public static final String TITLE = "업무 알림";
    public static final String TEMPLATE_VERSION = "1";
    private static final int MAX_ACTOR_LENGTH = 50;

    public NotificationContent create(NotificationType type, String actorName) {
        Objects.requireNonNull(type, "notificationType is required");
        String actor = normalizeActor(actorName);
        if (type.actorRequired() && actor == null) {
            throw new IllegalArgumentException("actorName is required for " + type);
        }
        String body = switch (type) {
            case TASK_COMMENT_CREATED -> "본인 업무에 댓글이 작성 되었습니다.";
            case TASK_MENTIONED -> actor + " 님이 업무에 당신을 언급하였습니다.";
            case COMMENT_MENTIONED -> actor + " 님이 댓글에 당신을 언급 하였습니다.";
            case SOURCE_OVERLAP -> "소스 겹침 알림";
            case NOTICE_CREATED -> "공지 사항이 등록 되었습니다.";
            case TASK_ARRIVED -> "업무가 도착 했습니다.";
            case APPROVAL_TASK_ARRIVED -> "결재할 업무가 도착 했습니다.";
            case MENTIONED_TASK_DEPLOYED -> "당신이 언급된 업무가 운영에 반영 되었습니다.";
        };
        return new NotificationContent(TITLE, body);
    }

    public String normalizeActor(String actorName) {
        if (actorName == null) return null;
        String value = Normalizer.normalize(actorName, Normalizer.Form.NFKC)
                .replaceAll("\\p{Cc}", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (value.isEmpty()) return null;
        return value.length() <= MAX_ACTOR_LENGTH ? value : value.substring(0, MAX_ACTOR_LENGTH);
    }

    public record NotificationContent(String title, String body) {}
}
