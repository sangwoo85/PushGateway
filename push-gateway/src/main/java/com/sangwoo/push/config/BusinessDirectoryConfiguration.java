package com.sangwoo.push.config;

import com.sangwoo.push.service.BusinessDirectory;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BusinessDirectoryConfiguration {
    @Bean
    @ConditionalOnMissingBean(BusinessDirectory.class)
    BusinessDirectory emptyBusinessDirectory() {
        return new BusinessDirectory() {
            public List<Entry> searchUsers(String query, int limit) { return List.of(); }
            public List<Entry> searchDepartments(String query, int limit) { return List.of(); }
        };
    }
}
