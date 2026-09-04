package com.example.pushgateway.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.pushgateway.config.PushProperties;
import com.example.pushgateway.config.SecurityConfiguration;
import com.example.pushgateway.domain.NotificationType;
import com.example.pushgateway.domain.PushTargetType;
import com.example.pushgateway.repository.PushQueueRepository;
import com.example.pushgateway.service.BusinessDirectory;
import com.example.pushgateway.service.EnrollmentQrService;
import com.example.pushgateway.service.PushQueueService;
import com.example.pushgateway.service.PushTestRateLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = PushTestController.class, properties = {
        "push.test-page.enabled=true",
        "push.test-page.authentication-enabled=false"
})
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties(PushProperties.class)
class PushTestControllerNoAuthTest {
    @Autowired MockMvc mvc;
    @MockitoBean PushQueueService queueService;
    @MockitoBean PushQueueRepository queue;
    @MockitoBean BusinessDirectory directory;
    @MockitoBean PushTestRateLimiter limiter;
    @MockitoBean EnrollmentQrService qr;
    @MockitoBean MeterRegistry metrics;

    @BeforeEach
    void setup() {
        when(metrics.counter(any(String.class), any(String[].class))).thenReturn(mock(Counter.class));
    }

    @Test
    void pagesAreAvailableWithoutLogin() throws Exception {
        mvc.perform(get("/internal/push-test/enrollment")).andExpect(status().isOk());
        mvc.perform(get("/internal/push-test/messages")).andExpect(status().isOk());
    }

    @Test
    void remoteRequestIsForbiddenWithoutLogin() throws Exception {
        mvc.perform(get("/internal/push-test/enrollment")
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.10");
                            return request;
                        }))
                .andExpect(status().isForbidden());
    }

    @Test
    void testPushCanBeQueuedWithoutLogin() throws Exception {
        when(queueService.enqueueForTest(any(PushTargetType.class), any(String.class),
                any(NotificationType.class), org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(UUID.randomUUID());

        mvc.perform(post("/internal/push-test/send").with(csrf())
                        .param("targetType", "USER")
                        .param("targetId", "user-1")
                        .param("notificationType", "TASK_ARRIVED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/internal/push-test/events/*"));
    }
}
