package com.example.pushgateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "push.test-page", name = "enabled", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain normalApplicationSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "push.test-page", name = "enabled", havingValue = "false", matchIfMissing = true)
    UserDetailsService noLocalUsers() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    @ConditionalOnProperty(prefix = "push.test-page", name = "enabled", havingValue = "true")
    SecurityFilterChain testPageSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/internal/**").hasAnyRole("PUSH_ADMIN", "PUSH_TESTER")
                        .anyRequest().permitAll())
                .formLogin(login -> login.defaultSuccessUrl("/internal/push-test", true))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "push.test-page", name = "enabled", havingValue = "true")
    PasswordEncoder pushTestPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnProperty(prefix = "push.test-page", name = "enabled", havingValue = "true")
    UserDetailsService pushTestUsers(PushProperties properties) {
        String username = properties.testPage().username();
        String hash = properties.testPage().passwordHash();
        if (username.isBlank() || !hash.matches("^\\$2[aby]\\$.+")) {
            throw new IllegalStateException(
                    "PUSH_TEST_PAGE_USERNAME and a BCrypt PUSH_TEST_PAGE_PASSWORD_HASH are required");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(hash).roles("PUSH_ADMIN").build());
    }
}
