package com.igsl.group.email_service_sample.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessageSummaryDTO {
    private Long id;
    private String messageId;
    private String from;
    private String subject;
    private LocalDateTime receivedDate;
    private boolean read;
    private boolean flagged;
    private boolean important;
    private boolean hasAttachments;
    private Set<String> labels = new HashSet<>();
    private String preview; // First 100 chars of text content
}