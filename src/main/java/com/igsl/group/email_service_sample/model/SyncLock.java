package com.igsl.group.email_service_sample.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;
import java.time.LocalDateTime;

@Entity
@Table(name = "sync_locks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncLock {
    @Id
    private String lockKey; // e.g., "imap_sync_INBOX"
    
    private String instanceId;
    private LocalDateTime acquiredAt;
    private LocalDateTime expiresAt;
    
    @Version
    private Long version; // Optimistic locking
    
    public SyncLock(String lockKey) {
        this.lockKey = lockKey;
    }
}