package com.example.pushgateway.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = PushTestController.class, properties = {
        "push.test-page.enabled=true",
        "push.test-page.username=admin",
        "push.test-page.password-hash=$2y$12$Ff7dhazqxmBc.qL1MTF4SOUoup83ps.XuvfLA3ugXU2VhJ.JdlgiG"
})
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties(PushProperties.class)
class PushTestControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean PushQueueService queueService;
    @MockitoBean PushQueueRepository queue;
    @MockitoBean BusinessDirectory directory;
    @MockitoBean PushTestRateLimiter limiter;
    @MockitoBean EnrollmentQrService qr;
    @MockitoBean MeterRegistry metrics;

    @BeforeEach
    void meter() {
        when(metrics.counter(any(String.class), any(String[].class))).thenReturn(mock(Counter.class));
    }

    @Test
    void unauthenticatedUserIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/internal/push-test")).andExpect(status().is3xxRedirection());
    }

    @Test
    void configuredBcryptPasswordCanAuthenticate() throws Exception {
        mvc.perform(formLogin().user("admin").password("public-test-only-password"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "USER")
    void userWithoutRoleIsForbidden() throws Exception {
        mvc.perform(get("/internal/push-test")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PUSH_ADMIN")
    void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/internal/push-test/send")
                .param("targetType", "USER").param("targetId", "user-1")
                .param("notificationType", "TASK_ARRIVED"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PUSH_ADMIN")
    void authorizedPostUsesQueueAndPrg() throws Exception {
        when(queueService.enqueueForTest(any(PushTargetType.class), any(String.class),
                any(NotificationType.class), org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(UUID.randomUUID());
        mvc.perform(post("/internal/push-test/send").with(csrf())
                .param("targetType", "USER").param("targetId", "user-1")
                .param("notificationType", "TASK_ARRIVED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/internal/push-test/events/*"));
    }

    @Test
    @WithMockUser(roles = "PUSH_ADMIN")
    void enrollmentAndMessagePagesAreAvailable() throws Exception {
        mvc.perform(get("/internal/push-test/enrollment"))
                .andExpect(status().isOk());
        mvc.perform(get("/internal/push-test/messages"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PUSH_ADMIN")
    void qrEndpointReturnsNoStorePng() throws Exception {
        when(qr.issue("user-1", "dept-1")).thenReturn(
                new EnrollmentQrService.IssuedQr(new byte[] {1, 2, 3}, Instant.now().plusSeconds(180)));
        mvc.perform(get("/internal/push-test/qr")
                        .param("userId", "user-1").param("departmentId", "dept-1"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType("image/png"));
    }

    @Test
    @WithMockUser(roles = "PUSH_ADMIN")
    void noticeRequiresConfirmationAndArbitraryTopicIsRejected() throws Exception {
        mvc.perform(post("/internal/push-test/send").with(csrf())
                .param("targetType", "NOTICE").param("notificationType", "NOTICE_CREATED")
                .param("topic", "attacker-topic"))
                .andExpect(status().is3xxRedirection());
    }
}
