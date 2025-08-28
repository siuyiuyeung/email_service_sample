package com.igsl.group.email_service_sample.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessageDTO {
    private Long id;
    private String messageId;
    private String from;
    private List<String> to = new ArrayList<>();
    private List<String> cc = new ArrayList<>();
    private List<String> bcc = new ArrayList<>();
    private String subject;
    private String textContent;
    private String htmlContent;
    private LocalDateTime sentDate;
    private LocalDateTime receivedDate;
    private String status;
    private String errorMessage;
    
    // Email client features
    private boolean read;
    private boolean flagged;
    private boolean important;
    private boolean spam;
    private boolean deleted;
    
    private LocalDateTime readDate;
    private LocalDateTime flaggedDate;
    
    // Related data
    private List<EmailAttachmentDTO> attachments = new ArrayList<>();
    private Set<EmailFolderDTO> folders = new HashSet<>();
    private Set<String> labels = new HashSet<>();
    
    // IMAP specific
    private Long imapUid;
    private String imapFolder;
    
    // S/MIME fields
    private boolean signed;
    private boolean encrypted;
    private boolean signatureValid;
    private String signerEmail;
    private LocalDateTime signatureTimestamp;
}