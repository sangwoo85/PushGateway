package com.sangwoo.push;

import com.sangwoo.push.config.PushProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties(PushProperties.class)
@SpringBootApplication
public class PushGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(PushGatewayApplication.class, args);
    }
}

