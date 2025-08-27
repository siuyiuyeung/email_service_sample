package com.igsl.group.email_service_sample.scheduler;

import com.igsl.group.email_service_sample.config.EmailProperties;
import com.igsl.group.email_service_sample.service.DistributedLockService;
import com.igsl.group.email_service_sample.service.EmailReceiverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Scheduler responsible for polling emails from IMAP server.
 * This class handles the scheduling aspect while delegating the actual
 * email synchronization to EmailReceiverService.
 */
@Component
@ConditionalOnProperty(
    value = "email.imap.polling.enabled",
    havingValue = "true",
    matchIfMissing = true
)
@RequiredArgsConstructor
@Slf4j
public class EmailPollingScheduler {
    
    private final EmailReceiverService emailReceiverService;
    private final EmailProperties emailProperties;
    private final DistributedLockService lockService;
    
    /**
     * Scheduled method to poll emails from IMAP server.
     * Uses distributed locking to prevent multiple instances from polling simultaneously.
     */
    @Scheduled(fixedDelayString = "${email.imap.poll-interval:60000}")
    public void pollEmails() {
        log.debug("Starting scheduled email polling");
        
        String lockKey = "imap_sync_" + emailProperties.getImap().getFolder();
        Duration lockDuration = Duration.ofMinutes(
            emailProperties.getImap().getPollLockDurationMinutes() != null 
                ? emailProperties.getImap().getPollLockDurationMinutes() 
                : 5
        );
        
        if (!lockService.acquireLock(lockKey, lockDuration)) {
            log.info("Another instance is already syncing folder: {}", 
                    emailProperties.getImap().getFolder());
            return;
        }
        
        try {
            log.info("Acquired lock for email polling, starting sync");
            emailReceiverService.performEmailSync();
            log.info("Email polling completed successfully");
        } catch (Exception e) {
            log.error("Error during scheduled email polling", e);
        } finally {
            lockService.releaseLock(lockKey);
            log.debug("Released lock for email polling");
        }
    }
    
    /**
     * Manual trigger for email polling.
     * Can be called programmatically when immediate sync is needed.
     */
    public void triggerEmailPoll() {
        log.info("Manual email poll triggered");
        pollEmails();
    }
}