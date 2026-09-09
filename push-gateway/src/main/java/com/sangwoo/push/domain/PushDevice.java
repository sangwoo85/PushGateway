package com.sangwoo.push.domain;

public record PushDevice(long id, String userId, Platform platform,
                         String registrationId, String appInstanceId) {}

