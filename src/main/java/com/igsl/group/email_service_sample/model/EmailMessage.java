package com.igsl.group.email_service_sample.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "email_messages")
public class EmailMessage {
    @Id
    private String messageId;
    
    private String from;
    
    @ElementCollection
    private List<String> to = new ArrayList<>();
    
    @ElementCollection
    private List<String> cc = new ArrayList<>();
    
    @ElementCollection
    private List<String> bcc = new ArrayList<>();
    
    private String subject;
    
    @Column(columnDefinition = "TEXT")
    private String textContent;
    
    @Column(columnDefinition = "TEXT")
    private String htmlContent;
    
    private LocalDateTime sentDate;
    private LocalDateTime receivedDate;
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<EmailAttachment> attachments = new ArrayList<>();
    
    @ElementCollection
    private Map<String, String> headers = new HashMap<>();
    
    @Enumerated(EnumType.STRING)
    private EmailStatus status;
    
    private String errorMessage;
    
    // Email client features
    private boolean isRead = false;
    private boolean isFlagged = false;
    private boolean isImportant = false;
    private boolean isSpam = false;
    private boolean isDeleted = false;
    
    @ManyToMany
    @JoinTable(name = "email_folder_mapping")
    private Set<EmailFolder> folders = new HashSet<>();
    
    @ElementCollection
    private Set<String> labels = new HashSet<>();
    
    private LocalDateTime readDate;
    private LocalDateTime flaggedDate;
    
    // IMAP specific
    private Long imapUid;
    private String imapFolder;
    
    // S/MIME fields
    private boolean isSigned = false;
    private boolean isEncrypted = false;
    private boolean signatureValid;
    private String signerEmail;
    private String signerCertificateThumbprint;
    private LocalDateTime signatureTimestamp;
    
    @ElementCollection
    private List<String> encryptionRecipients = new ArrayList<>();
    
    @Enumerated(EnumType.STRING)
    private SmimeStatus smimeStatus;
    
    @Column(columnDefinition = "TEXT")
    private String smimeErrors;
    
    // Original message for S/MIME operations
    @Lob
    private byte[] originalMimeContent;
}