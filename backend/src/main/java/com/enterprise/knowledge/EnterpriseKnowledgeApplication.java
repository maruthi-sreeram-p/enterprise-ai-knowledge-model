package com.enterprise.knowledge;

import com.enterprise.knowledge.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableAsync
public class EnterpriseKnowledgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseKnowledgeApplication.class, args);
    }
}
