package com.igsl.group.email_service_sample.controller;

import com.igsl.group.email_service_sample.scheduler.LockCleanupScheduler;
import com.igsl.group.email_service_sample.service.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for managing distributed locks.
 * Provides administrative endpoints for lock maintenance operations.
 */
@RestController
@RequestMapping("/api/v1/locks")
@ConditionalOnBean(LockCleanupScheduler.class)
@RequiredArgsConstructor
@Slf4j
public class LockManagementController {
    
    private final LockCleanupScheduler lockCleanupScheduler;
    private final DistributedLockService distributedLockService;
    
    /**
     * Manually trigger cleanup of expired locks.
     * This endpoint allows administrators to force immediate cleanup of expired locks.
     * 
     * @return Response with the number of locks cleaned up
     */
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupExpiredLocks() {
        log.info("Manual lock cleanup triggered via REST API");
        
        Map<String, Object> response = new HashMap<>();
        try {
            int cleanedCount = lockCleanupScheduler.triggerCleanup();
            response.put("status", "success");
            response.put("message", "Lock cleanup completed successfully");
            response.put("cleanedCount", cleanedCount);
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during manual lock cleanup", e);
            response.put("status", "error");
            response.put("message", "Failed to cleanup locks: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}