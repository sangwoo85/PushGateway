package com.example.pushgateway.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnProperty(prefix = "push.test-page", name = "enabled", havingValue = "true")
class TestPageHomeController {
    @GetMapping("/")
    String home() {
        return "redirect:/internal/push-test";
    }
}
