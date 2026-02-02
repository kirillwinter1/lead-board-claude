package com.leadboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LeadBoardApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeadBoardApplication.class, args);
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfiguration {
    }
}
