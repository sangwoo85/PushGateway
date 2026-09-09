package com.sangwoo.push.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PushTestDisabledTest {
    @Test
    void controllerBeanIsAbsentByDefault() {
        new ApplicationContextRunner().withUserConfiguration(PushTestController.class)
                .withPropertyValues("push.test-page.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(PushTestController.class));
    }
}
