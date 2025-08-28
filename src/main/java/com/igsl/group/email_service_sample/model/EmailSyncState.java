package com.igsl.group.email_service_sample.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Table(name = "email_sync_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSyncState {
    @Id
    private String folderName;
    
    private Date lastSyncDate;
    private Long lastUidValidity;
    private Long highestUidSeen;
    private LocalDateTime lastModified;
    private Long messagesProcessed;
    private Long totalMessagesFound;
    private Date lastProcessedMessageDate;
    
    public EmailSyncState(String folderName) {
        this.folderName = folderName;
        this.lastSyncDate = new Date(0); // Start from epoch
        this.lastModified = LocalDateTime.now();
    }
}