package com.igsl.group.email_service_sample.service;

import com.igsl.group.email_service_sample.model.SyncLock;
import com.igsl.group.email_service_sample.repository.SyncLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {
    private final SyncLockRepository lockRepository;
    private final String instanceId = UUID.randomUUID().toString();
    
    @Transactional
    public void cleanupExpiredLocks() {
        try {
            lockRepository.deleteExpiredLocks(LocalDateTime.now());
            log.info("Cleaned up expired locks");
        } catch (Exception e) {
            log.error("Error cleaning up expired locks", e);
        }
    }
    
    // Run cleanup every 5 minutes
    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void scheduledCleanup() {
        try {
            lockRepository.deleteExpiredLocks(LocalDateTime.now());
            log.debug("Scheduled cleanup: cleaned up expired locks");
        } catch (Exception e) {
            log.error("Error during scheduled cleanup of expired locks", e);
        }
    }
    
    @Transactional
    public boolean acquireLock(String lockKey, Duration duration) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(duration);
        
        // First, try to find existing lock
        Optional<SyncLock> existingLock = lockRepository.findById(lockKey);
        
        if (existingLock.isPresent()) {
            SyncLock lock = existingLock.get();
            
            // Check if lock is held by this instance
            if (instanceId.equals(lock.getInstanceId())) {
                // Refresh our own lock
                lock.setExpiresAt(expiresAt);
                lock.setAcquiredAt(now);
                lockRepository.save(lock);
                return true;
            }
            
            // Check if lock is expired
            if (lock.getExpiresAt().isBefore(now)) {
                // Lock expired, try to acquire
                lock.setInstanceId(instanceId);
                lock.setAcquiredAt(now);
                lock.setExpiresAt(expiresAt);
                try {
                    lockRepository.save(lock);
                    log.debug("Acquired expired lock: {}", lockKey);
                    return true;
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.debug("Failed to acquire expired lock due to concurrent access: {}", lockKey);
                    return false;
                }
            }
            
            // Lock is held by another instance and not expired
            log.debug("Lock {} is held by instance {} until {}", lockKey, lock.getInstanceId(), lock.getExpiresAt());
            return false;
        } else {
            // No existing lock, create new one
            try {
                SyncLock lock = new SyncLock();
                lock.setLockKey(lockKey);
                lock.setInstanceId(instanceId);
                lock.setAcquiredAt(now);
                lock.setExpiresAt(expiresAt);
                
                lockRepository.save(lock);
                log.debug("Acquired new lock: {}", lockKey);
                return true;
            } catch (DataIntegrityViolationException e) {
                // Another instance created the lock concurrently
                log.debug("Failed to create new lock due to concurrent creation: {}", lockKey);
                return false;
            }
        }
    }
    
    @Transactional
    public void releaseLock(String lockKey) {
        try {
            Optional<SyncLock> lock = lockRepository.findById(lockKey);
            if (lock.isPresent() && instanceId.equals(lock.get().getInstanceId())) {
                lockRepository.deleteById(lockKey);
                log.debug("Released lock: {}", lockKey);
            }
        } catch (Exception e) {
            log.error("Error releasing lock: {}", lockKey, e);
        }
    }
}