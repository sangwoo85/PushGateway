package com.example.pushgateway.domain;

public enum NotificationType {
    TASK_COMMENT_CREATED(false),
    TASK_MENTIONED(true),
    COMMENT_MENTIONED(true),
    SOURCE_OVERLAP(false),
    NOTICE_CREATED(false),
    TASK_ARRIVED(false),
    APPROVAL_TASK_ARRIVED(false),
    MENTIONED_TASK_DEPLOYED(false);

    private final boolean actorRequired;

    NotificationType(boolean actorRequired) {
        this.actorRequired = actorRequired;
    }

    public boolean actorRequired() {
        return actorRequired;
    }

    public String wireName() {
        return switch (this) {
            case TASK_COMMENT_CREATED -> "COMMENT_ADDED";
            case SOURCE_OVERLAP -> "SOURCE_CONFLICT";
            case NOTICE_CREATED -> "NOTICE_REGISTERED";
            default -> name();
        };
    }

    public String displayName() {
        return switch (this) {
            case TASK_COMMENT_CREATED -> "본인 업무에 댓글이 작성되었습니다.";
            case TASK_MENTIONED -> "{행위자} 님이 업무에 당신을 언급하였습니다.";
            case COMMENT_MENTIONED -> "{행위자} 님이 댓글에 당신을 언급하였습니다.";
            case SOURCE_OVERLAP -> "소스 겹침 알림";
            case NOTICE_CREATED -> "공지 사항이 등록되었습니다.";
            case TASK_ARRIVED -> "업무가 도착했습니다.";
            case APPROVAL_TASK_ARRIVED -> "결재할 업무가 도착했습니다.";
            case MENTIONED_TASK_DEPLOYED -> "당신이 언급된 업무가 운영에 반영되었습니다.";
        };
    }
}
