package com.igsl.group.email_service_sample.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration class for enabling scheduling in the application.
 * This configuration is separated to allow easy enabling/disabling of scheduling features.
 */
@Configuration
@EnableScheduling
public class SchedulerConfiguration {
    // This class enables Spring's scheduled task execution capability
    // Additional scheduler configurations can be added here if needed
}