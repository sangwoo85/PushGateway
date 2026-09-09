package com.sangwoo.push.api;

import com.sangwoo.push.config.PushProperties;
import com.sangwoo.push.config.SecurityConfiguration;
import com.sangwoo.push.service.ProductionEnrollmentService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;

@WebMvcTest(value = EnrollmentController.class, properties = {
        "push.test-page.enabled=true", "push.test-page.authentication-enabled=false"})
@Import({SecurityConfiguration.class, ProductionEnrollmentService.class})
@EnableConfigurationProperties(PushProperties.class)
class EnrollmentWithUnsecuredTestPageTest extends EnrollmentContract {}
