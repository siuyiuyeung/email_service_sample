package com.igsl.group.email_service_sample.controller;

import com.igsl.group.email_service_sample.model.EmailSyncState;
import com.igsl.group.email_service_sample.repository.EmailSyncStateRepository;
import com.igsl.group.email_service_sample.scheduler.EmailPollingScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final EmailSyncStateRepository syncStateRepository;
    
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
    
    /**
     * Get sync progress for all folders.
     * 
     * @return Sync progress details for all folders
     */
    @GetMapping("/progress")
    public ResponseEntity<Map<String, Object>> getSyncProgress() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<EmailSyncState> syncStates = syncStateRepository.findAll();
            
            List<Map<String, Object>> folderProgress = syncStates.stream()
                .map(state -> {
                    Map<String, Object> progress = new HashMap<>();
                    progress.put("folderName", state.getFolderName());
                    progress.put("lastSyncDate", state.getLastSyncDate());
                    progress.put("lastModified", state.getLastModified());
                    progress.put("messagesProcessed", state.getMessagesProcessed());
                    progress.put("totalMessagesFound", state.getTotalMessagesFound());
                    progress.put("lastProcessedMessageDate", state.getLastProcessedMessageDate());
                    
                    // Calculate progress percentage
                    if (state.getTotalMessagesFound() != null && state.getTotalMessagesFound() > 0) {
                        long processed = state.getMessagesProcessed() != null ? state.getMessagesProcessed() : 0;
                        double percentage = (processed * 100.0) / state.getTotalMessagesFound();
                        progress.put("progressPercentage", String.format("%.1f", percentage));
                    } else {
                        progress.put("progressPercentage", "0.0");
                    }
                    
                    return progress;
                })
                .collect(Collectors.toList());
            
            response.put("folders", folderProgress);
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error fetching sync progress", e);
            response.put("error", "Failed to fetch sync progress: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Get sync progress for a specific folder.
     * 
     * @param folderName The name of the folder
     * @return Sync progress details for the specified folder
     */
    @GetMapping("/progress/{folderName}")
    public ResponseEntity<Map<String, Object>> getFolderSyncProgress(@PathVariable String folderName) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            EmailSyncState syncState = syncStateRepository.findByFolderName(folderName)
                .orElse(null);
            
            if (syncState == null) {
                response.put("error", "Folder not found or never synced: " + folderName);
                return ResponseEntity.notFound().build();
            }
            
            response.put("folderName", syncState.getFolderName());
            response.put("lastSyncDate", syncState.getLastSyncDate());
            response.put("lastModified", syncState.getLastModified());
            response.put("messagesProcessed", syncState.getMessagesProcessed());
            response.put("totalMessagesFound", syncState.getTotalMessagesFound());
            response.put("lastProcessedMessageDate", syncState.getLastProcessedMessageDate());
            response.put("lastUidValidity", syncState.getLastUidValidity());
            response.put("highestUidSeen", syncState.getHighestUidSeen());
            
            // Calculate progress percentage
            if (syncState.getTotalMessagesFound() != null && syncState.getTotalMessagesFound() > 0) {
                long processed = syncState.getMessagesProcessed() != null ? syncState.getMessagesProcessed() : 0;
                double percentage = (processed * 100.0) / syncState.getTotalMessagesFound();
                response.put("progressPercentage", String.format("%.1f", percentage));
                response.put("isComplete", processed >= syncState.getTotalMessagesFound());
            } else {
                response.put("progressPercentage", "0.0");
                response.put("isComplete", true);
            }
            
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error fetching sync progress for folder: " + folderName, e);
            response.put("error", "Failed to fetch sync progress: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}