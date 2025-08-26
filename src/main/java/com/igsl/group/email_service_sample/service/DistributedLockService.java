package com.igsl.group.email_service_sample.service;

import com.igsl.group.email_service_sample.model.SyncLock;
import com.igsl.group.email_service_sample.repository.SyncLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {
    private final SyncLockRepository lockRepository;
    private final String instanceId = UUID.randomUUID().toString();
    
    @Transactional
    public boolean acquireLock(String lockKey, Duration duration) {
        try {
            SyncLock lock = new SyncLock();
            lock.setLockKey(lockKey);
            lock.setInstanceId(instanceId);
            lock.setAcquiredAt(LocalDateTime.now());
            lock.setExpiresAt(LocalDateTime.now().plus(duration));
            
            lockRepository.save(lock);
            return true;
        } catch (DataIntegrityViolationException e) {
            // Lock already exists
            return tryAcquireExistingLock(lockKey, duration);
        }
    }
    
    @Transactional
    public void releaseLock(String lockKey) {
        lockRepository.deleteById(lockKey);
    }
    
    private boolean tryAcquireExistingLock(String lockKey, Duration duration) {
        return lockRepository.findById(lockKey)
            .map(lock -> {
                if (lock.getExpiresAt().isBefore(LocalDateTime.now())) {
                    // Lock expired, try to acquire
                    lock.setInstanceId(instanceId);
                    lock.setAcquiredAt(LocalDateTime.now());
                    lock.setExpiresAt(LocalDateTime.now().plus(duration));
                    try {
                        lockRepository.save(lock);
                        return true;
                    } catch (ObjectOptimisticLockingFailureException e) {
                        return false;
                    }
                }
                return false;
            })
            .orElse(false);
    }
}