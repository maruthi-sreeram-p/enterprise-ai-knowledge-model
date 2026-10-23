package com.enterprise.knowledge;

import com.enterprise.knowledge.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

// Sign-in is handled by AuthService and JwtAuthenticationFilter, not by Spring's
// username/password provider, so the auto-configured default user (and the
// generated password it prints at startup) is not needed.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(AppProperties.class)
@EnableAsync
public class EnterpriseKnowledgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseKnowledgeApplication.class, args);
    }
}
