package com.igsl.group.email_service_sample.controller;

import com.igsl.group.email_service_sample.scheduler.EmailPollingScheduler;
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
 * REST controller for managing email polling operations.
 * Provides endpoints to manually trigger email synchronization.
 */
@RestController
@RequestMapping("/api/v1/email-polling")
@ConditionalOnBean(EmailPollingScheduler.class)
@RequiredArgsConstructor
@Slf4j
public class EmailPollingController {
    
    private final EmailPollingScheduler emailPollingScheduler;
    
    /**
     * Manually trigger email polling.
     * This endpoint allows administrators to force an immediate email sync.
     * 
     * @return Response indicating the polling was triggered
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, String>> triggerEmailPolling() {
        log.info("Manual email polling triggered via REST API");
        
        Map<String, String> response = new HashMap<>();
        try {
            emailPollingScheduler.triggerEmailPoll();
            response.put("status", "success");
            response.put("message", "Email polling triggered successfully");
            response.put("timestamp", String.valueOf(System.currentTimeMillis()));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error triggering email polling", e);
            response.put("status", "error");
            response.put("message", "Failed to trigger email polling: " + e.getMessage());
            response.put("timestamp", String.valueOf(System.currentTimeMillis()));
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Get the current polling configuration.
     * 
     * @return Current polling configuration details
     */
    @RequestMapping("/status")
    public ResponseEntity<Map<String, Object>> getPollingStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("pollingEnabled", true); // Since this controller exists only when polling is enabled
        status.put("message", "Email polling is active");
        status.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(status);
    }
}