package com.example.pushgateway.config;

import java.net.InetAddress;
import org.springframework.security.authorization.AuthorizationDecision;
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
    SecurityFilterChain testPageSecurity(HttpSecurity http, PushProperties properties) throws Exception {
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"));
        if (!properties.testPage().authenticationEnabled()) {
            return http.authorizeHttpRequests(auth -> auth
                            .requestMatchers("/internal/**").access((authentication, context) -> {
                                try {
                                    return new AuthorizationDecision(InetAddress
                                            .getByName(context.getRequest().getRemoteAddr()).isLoopbackAddress());
                                } catch (Exception ignored) {
                                    return new AuthorizationDecision(false);
                                }
                            })
                            .anyRequest().permitAll())
                    .formLogin(login -> login.disable())
                    .build();
        }
        return http.authorizeHttpRequests(auth -> auth
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
        if (!properties.testPage().authenticationEnabled()) {
            return new InMemoryUserDetailsManager();
        }
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
