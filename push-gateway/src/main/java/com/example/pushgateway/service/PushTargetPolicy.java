package com.example.pushgateway.service;

import com.example.pushgateway.domain.NotificationType;
import com.example.pushgateway.domain.PushTargetType;
import java.util.EnumSet;
import org.springframework.stereotype.Component;

/** 운영 생산 코드의 대상별 허용 정책. 테스트 화면은 별도 권한 아래 이 정책만 완화한다. */
@Component
public class PushTargetPolicy {
    private static final EnumSet<NotificationType> DEPARTMENT_TYPES = EnumSet.of(
            NotificationType.SOURCE_OVERLAP, NotificationType.NOTICE_CREATED,
            NotificationType.TASK_ARRIVED);

    public void validate(PushTargetType targetType, NotificationType notificationType) {
        boolean allowed = switch (targetType) {
            case USER -> true;
            case DEPARTMENT -> DEPARTMENT_TYPES.contains(notificationType);
            case NOTICE -> notificationType == NotificationType.NOTICE_CREATED;
        };
        if (!allowed) throw new IllegalArgumentException(
                notificationType + " is not allowed for operational " + targetType + " delivery");
    }
}
