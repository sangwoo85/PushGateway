package com.example.pushgateway.api;

import com.example.pushgateway.config.PushProperties;
import com.example.pushgateway.domain.NotificationType;
import com.example.pushgateway.domain.PushTargetType;
import com.example.pushgateway.repository.PushQueueRepository;
import com.example.pushgateway.service.BusinessDirectory;
import com.example.pushgateway.service.EnrollmentQrService;
import com.example.pushgateway.service.PushQueueService;
import com.example.pushgateway.service.PushTestRateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/internal/push-test")
@ConditionalOnProperty(prefix = "push.test-page", name = "enabled", havingValue = "true")
public class PushTestController {
    private static final Logger audit = LoggerFactory.getLogger("PUSH_AUDIT");
    private final PushQueueService queueService;
    private final PushQueueRepository queue;
    private final BusinessDirectory directory;
    private final PushProperties properties;
    private final PushTestRateLimiter limiter;
    private final EnrollmentQrService qr;
    private final MeterRegistry metrics;

    public PushTestController(PushQueueService queueService, PushQueueRepository queue,
                              BusinessDirectory directory, PushProperties properties,
                              PushTestRateLimiter limiter, EnrollmentQrService qr,
                              MeterRegistry metrics) {
        this.queueService = queueService;
        this.queue = queue;
        this.directory = directory;
        this.properties = properties;
        this.limiter = limiter;
        this.qr = qr;
        this.metrics = metrics;
    }

    @GetMapping
    String home() {
        return "redirect:/internal/push-test/enrollment";
    }

    @GetMapping("/enrollment")
    String enrollment(@RequestParam(defaultValue = "") String userId,
                      @RequestParam(defaultValue = "") String departmentId, Model model) {
        String normalizedUserId = normalizePageId(userId);
        String normalizedDepartmentId = normalizePageId(departmentId);
        model.addAttribute("userId", normalizedUserId);
        model.addAttribute("departmentId", normalizedDepartmentId);
        model.addAttribute("qrReady", !normalizedUserId.isBlank() && !normalizedDepartmentId.isBlank());
        model.addAttribute("qrTtlSeconds", properties.qr().ttl().toSeconds());
        return "push-enrollment";
    }

    @GetMapping("/messages")
    String messages(@RequestParam(defaultValue = "") String q,
                @RequestParam(defaultValue = "USER") PushTargetType searchType,
                @RequestParam(defaultValue = "") String selectedTarget, Model model) {
        model.addAttribute("form", new SendForm(searchType, selectedTarget, NotificationType.TASK_ARRIVED, null, false));
        model.addAttribute("types", NotificationType.values());
        model.addAttribute("targetTypes", PushTargetType.values());
        model.addAttribute("results", searchType == PushTargetType.DEPARTMENT
                ? directory.searchDepartments(q, 50) : directory.searchUsers(q, 50));
        model.addAttribute("searchType", searchType);
        model.addAttribute("firebaseEnabled", properties.firebase().enabled());
        return "push-test";
    }

    @PostMapping("/send")
    String send(@Valid @ModelAttribute("form") SendForm form, BindingResult binding,
                Principal principal, RedirectAttributes redirect, HttpServletRequest request) {
        if (request.getParameter("topic") != null) binding.reject("topic", "임의 Topic은 입력할 수 없습니다.");
        if ((form.targetType() == PushTargetType.DEPARTMENT || form.targetType() == PushTargetType.NOTICE)
                && !Boolean.TRUE.equals(form.confirmed())) binding.reject("confirmation", "다수 대상 발송 확인이 필요합니다.");
        if (!properties.firebase().enabled() && !properties.testPage().enqueueWhenFirebaseDisabled()) {
            binding.reject("firebase", "현재 FCM 발송이 비활성화되어 Queue 등록도 중지되었습니다.");
        }
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("error", binding.getAllErrors().getFirst().getDefaultMessage());
            return "redirect:/internal/push-test/messages";
        }
        try {
            String actor = principal == null ? "local-test" : principal.getName();
            limiter.check(actor);
            UUID eventId = queueService.enqueueForTest(form.targetType(), form.targetId(),
                    form.notificationType(), form.actorName());
            audit.info("actor={} targetType={} target={} notificationType={} eventId={}",
                    actor, form.targetType(), mask(form.targetId()),
                    form.notificationType(), eventId);
            metrics.counter("push.test.request", "target_type", form.targetType().name()).increment();
            return "redirect:/internal/push-test/events/" + eventId;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/internal/push-test/messages";
        }
    }

    @GetMapping("/events/{eventId}")
    String result(@org.springframework.web.bind.annotation.PathVariable UUID eventId, Model model) {
        model.addAttribute("event", queue.findByEventId(eventId));
        return "push-test-result";
    }

    @GetMapping(value = "/qr", produces = MediaType.IMAGE_PNG_VALUE)
    ResponseEntity<byte[]> qr(@RequestParam @Size(max = 128) String userId,
                              @RequestParam @Size(max = 128) String departmentId) {
        var issued = qr.issue(userId, departmentId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(issued.png());
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) return "ALL";
        return value.length() < 4 ? "***" : value.substring(0, 2) + "***" + value.substring(value.length() - 1);
    }

    private String normalizePageId(String value) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("ID는 128자 이하여야 합니다.");
        }
        return normalized;
    }

    public record SendForm(@NotNull PushTargetType targetType,
                           @Size(max = 128) String targetId,
                           @NotNull NotificationType notificationType,
                           @Size(max = 50) String actorName,
                           Boolean confirmed) {}
}
