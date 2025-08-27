package com.igsl.group.email_service_sample.scheduler;

import com.igsl.group.email_service_sample.repository.SyncLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduler responsible for cleaning up expired distributed locks.
 * This ensures that locks from crashed instances or failed operations
 * are eventually released.
 */
@Component
@ConditionalOnProperty(
    value = "distributed-lock.cleanup.enabled",
    havingValue = "true",
    matchIfMissing = true
)
@RequiredArgsConstructor
@Slf4j
public class LockCleanupScheduler {
    
    private final SyncLockRepository lockRepository;
    
    /**
     * Scheduled cleanup of expired locks.
     * Runs every 5 minutes by default, configurable via properties.
     */
    @Scheduled(fixedDelayString = "${distributed-lock.cleanup.interval:300000}")
    @Transactional
    public void cleanupExpiredLocks() {
        try {
            LocalDateTime now = LocalDateTime.now();
            int deletedCount = lockRepository.deleteExpiredLocks(now);
            
            if (deletedCount > 0) {
                log.info("Cleaned up {} expired distributed locks", deletedCount);
            } else {
                log.debug("No expired locks found during scheduled cleanup");
            }
        } catch (Exception e) {
            log.error("Error during scheduled cleanup of expired locks", e);
        }
    }
    
    /**
     * Manual trigger for lock cleanup.
     * Can be called programmatically when immediate cleanup is needed.
     * 
     * @return Number of locks cleaned up
     */
    @Transactional
    public int triggerCleanup() {
        log.info("Manual lock cleanup triggered");
        try {
            LocalDateTime now = LocalDateTime.now();
            int deletedCount = lockRepository.deleteExpiredLocks(now);
            log.info("Manual cleanup removed {} expired locks", deletedCount);
            return deletedCount;
        } catch (Exception e) {
            log.error("Error during manual lock cleanup", e);
            return 0;
        }
    }
}