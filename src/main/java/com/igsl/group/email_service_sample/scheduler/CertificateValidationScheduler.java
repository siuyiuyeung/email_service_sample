package com.igsl.group.email_service_sample.scheduler;

import com.igsl.group.email_service_sample.config.EmailProperties;
import com.igsl.group.email_service_sample.service.SMimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily scheduled job that marks expired S/MIME certificates in the database.
 * Only active when S/MIME is enabled (email.smime.enabled=true).
 */
@Component
@ConditionalOnProperty(value = "email.smime.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CertificateValidationScheduler {

    private final SMimeService smimeService;
    private final EmailProperties properties;

    /**
     * Run daily at 02:00 to refresh certificate statuses.
     */
    @Scheduled(cron = "${email.smime.validation-cron:0 0 2 * * *}")
    public void validateCertificates() {
        log.info("Running scheduled S/MIME certificate validation");
        try {
            smimeService.validateCertificateStatuses();
            log.info("S/MIME certificate validation completed");
        } catch (Exception e) {
            log.error("Error during scheduled S/MIME certificate validation", e);
        }
    }
}
