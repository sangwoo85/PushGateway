package com.example.pushgateway.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "push.firebase", name = "enabled", havingValue = "true")
public class FirebaseConfiguration {
    @Bean
    FirebaseApp firebaseApp(PushProperties properties) throws IOException {
        FirebaseOptions.Builder builder = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault());
        if (!properties.firebase().projectId().isBlank()) {
            builder.setProjectId(properties.firebase().projectId());
        }
        return FirebaseApp.initializeApp(builder.build());
    }

    @Bean
    FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}

