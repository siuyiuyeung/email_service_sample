package com.igsl.group.email_service_sample.model;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "email_messages", indexes = {
    @Index(name = "idx_imap_uid_folder", columnList = "imapUid,imapFolder")
})
@EqualsAndHashCode(exclude = {"attachments", "folders"})
@ToString(exclude = {"attachments", "folders"})
public class EmailMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(columnDefinition = "VARCHAR(1000)")
    private String messageId;

    @Column(name = "from_address", length = 500)
    private String from;

    @Column(name = "to_addresses")
    @ElementCollection
    @Builder.Default
    private List<String> to = new ArrayList<>();

    @Column(name = "cc_addresses")
    @ElementCollection
    @Builder.Default
    private List<String> cc = new ArrayList<>();

    @Column(name = "bcc_addresses")
    @ElementCollection
    @Builder.Default
    private List<String> bcc = new ArrayList<>();
    
    @Column(length = 1000)
    private String subject;
    
    @Column(columnDefinition = "TEXT")
    private String textContent;
    
    @Column(columnDefinition = "TEXT")
    private String htmlContent;
    
    private LocalDateTime sentDate;
    private LocalDateTime receivedDate;
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "email_message_id")
    @Builder.Default
    private List<EmailAttachment> attachments = new ArrayList<>();
    
    @ElementCollection
    @CollectionTable(name = "email_message_headers", 
                     joinColumns = @JoinColumn(name = "email_message_id"))
    @MapKeyColumn(name = "header_key")
    @Column(name = "header_value", columnDefinition = "TEXT")
    @Builder.Default
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
    @Builder.Default
    private Set<EmailFolder> folders = new HashSet<>();
    
    @ElementCollection
    @Builder.Default
    private Set<String> labels = new HashSet<>();
    
    private LocalDateTime readDate;
    private LocalDateTime flaggedDate;
    
    // IMAP specific
    private Long imapUid;
    @Column(length = 500)
    private String imapFolder;
    
    // S/MIME fields
    private boolean isSigned = false;
    private boolean isEncrypted = false;
    private boolean signatureValid;
    private String signerEmail;
    private String signerCertificateThumbprint;
    private LocalDateTime signatureTimestamp;
    
    @ElementCollection
    @Builder.Default
    private List<String> encryptionRecipients = new ArrayList<>();
    
    @Enumerated(EnumType.STRING)
    private SmimeStatus smimeStatus;
    
    @Column(columnDefinition = "TEXT")
    private String smimeErrors;
    
    // Original message for S/MIME operations
    @Lob
    private byte[] originalMimeContent;
}