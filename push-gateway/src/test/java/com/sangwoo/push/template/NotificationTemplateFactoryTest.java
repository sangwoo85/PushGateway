package com.sangwoo.push.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangwoo.push.domain.NotificationType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationTemplateFactoryTest {
    private final NotificationTemplateFactory factory = new NotificationTemplateFactory();

    @Test
    void createsOnlyTheEightFixedMessages() {
        Map<NotificationType, String> expected = Map.of(
                NotificationType.TASK_COMMENT_CREATED, "본인 업무에 댓글이 작성 되었습니다.",
                NotificationType.TASK_MENTIONED, "홍길동 님이 업무에 당신을 언급하였습니다.",
                NotificationType.COMMENT_MENTIONED, "홍길동 님이 댓글에 당신을 언급 하였습니다.",
                NotificationType.SOURCE_OVERLAP, "소스 겹침 알림",
                NotificationType.NOTICE_CREATED, "공지 사항이 등록 되었습니다.",
                NotificationType.TASK_ARRIVED, "업무가 도착 했습니다.",
                NotificationType.APPROVAL_TASK_ARRIVED, "결재할 업무가 도착 했습니다.",
                NotificationType.MENTIONED_TASK_DEPLOYED, "당신이 언급된 업무가 운영에 반영 되었습니다.");

        expected.forEach((type, body) -> {
            var content = factory.create(type, type.actorRequired() ? "홍길동" : null);
            assertThat(content.title()).isEqualTo("업무 알림");
            assertThat(content.body()).isEqualTo(body);
        });
    }

    @Test
    void normalizesControlsWhitespaceUnicodeAndLength() {
        String input = "  Ａ\u0000   " + "가".repeat(60) + "  ";
        String normalized = factory.normalizeActor(input);
        assertThat(normalized).startsWith("A ").hasSize(50).doesNotContain("\u0000", "  ");
    }

    @Test
    void rejectsMissingActorAndUnknownEnumName() {
        assertThatThrownBy(() -> factory.create(NotificationType.TASK_MENTIONED, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationType.valueOf("ARBITRARY_MESSAGE"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

