# Email Service Design Documentation

## System Architecture Overview

The Email Service is a Spring Boot application that demonstrates email operations using JavaMail API with support for:
- Sending emails via SMTP
- Receiving emails via IMAP with email client features
- RESTful API for email operations
- Asynchronous email processing
- Email template support
- Email marking system (read/unread, flagged, important)
- Folder/Label organization
- Email metadata persistence

```
┌─────────────────────────────────────────────────────────────┐
│                    Email Service Application                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────┐  ┌──────────────────┐  ┌────────────┐  │
│  │   REST API      │  │  Email Listener  │  │  Scheduler │  │
│  │  Controller     │  │   Component      │  │  Component │  │
│  └────────┬────────┘  └────────┬─────────┘  └─────┬──────┘  │
│           │                    │                  │         │
│  ┌────────▼────────────────────▼──────────────────▼──────┐  │
│  │                  Service Layer                        │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │  │
│  │  │ Email Sender │  │Email Receiver│  │Email Marking │ │  │
│  │  │   Service    │  │   Service    │  │   Service    │ │  │
│  │  └──────────────┘  └──────────────┘  └──────────────┘ │  │
│  │  ┌──────────────┐  ┌──────────────┐                   │  │
│  │  │Email Template│  │ Email Folder │                   │  │
│  │  │   Service    │  │   Service    │                   │  │
│  │  └──────────────┘  └──────────────┘                   │  │
│  └─────────────────────────┬─────────────────────────────┘  │
│                            │                                │
│  ┌─────────────────────────▼──────────────────────────────┐ │
│  │               Data Persistence Layer                   │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │ │
│  │  │   JPA/H2     │  │Email Metadata│  │ Email Flags  │  │ │
│  │  │  Repository  │  │  Repository  │  │  Repository  │  │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘  │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Component Design

### 1. Configuration Layer

#### EmailProperties.java
```java
@ConfigurationProperties(prefix = "email")
@Component
@Data
public class EmailProperties {
    private Smtp smtp = new Smtp();
    private Imap imap = new Imap();
    private General general = new General();
    
    @Data
    public static class Smtp {
        private String host;
        private int port = 587;
        private String username;
        private String password;
        private boolean auth = true;
        private boolean starttls = true;
        private int connectionTimeout = 5000;
        private int timeout = 5000;
    }
    
    @Data
    public static class Imap {
        private String host;
        private int port = 993;
        private String username;
        private String password;
        private String folder = "INBOX";
        private boolean ssl = true;
        private int pollInterval = 60000; // 1 minute
    }
    
    @Data
    public static class General {
        private String fromAddress;
        private String fromName;
        private boolean debugEnabled = false;
        private int maxRetries = 3;
    }
}
```

#### EmailConfiguration.java
```java
@Configuration
@EnableAsync
@EnableScheduling
public class EmailConfiguration {
    
    @Bean
    public JavaMailSender javaMailSender(EmailProperties properties) {
        // Configure SMTP mail sender
    }
    
    @Bean
    public Store imapStore(EmailProperties properties) {
        // Configure IMAP store connection
    }
    
    @Bean
    public TaskExecutor emailTaskExecutor() {
        // Configure async executor for email operations
    }
}
```

### 2. Data Models

#### EmailMessage.java
```java
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
    private List<String> to;
    
    @ElementCollection
    private List<String> cc;
    
    @ElementCollection
    private List<String> bcc;
    
    private String subject;
    
    @Column(columnDefinition = "TEXT")
    private String textContent;
    
    @Column(columnDefinition = "TEXT")
    private String htmlContent;
    
    private LocalDateTime sentDate;
    private LocalDateTime receivedDate;
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<EmailAttachment> attachments;
    
    @ElementCollection
    private Map<String, String> headers;
    
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
}
```

#### EmailAttachment.java
```java
@Data
@Builder
@Entity
@Table(name = "email_attachments")
public class EmailAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String filename;
    private String contentType;
    
    @Lob
    private byte[] content;
    
    private long size;
}
```

#### EmailFolder.java
```java
@Data
@Builder
@Entity
@Table(name = "email_folders")
public class EmailFolder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private String displayName;
    private String icon;
    private FolderType type;
    private int displayOrder;
    
    @ManyToMany(mappedBy = "folders")
    private Set<EmailMessage> emails = new HashSet<>();
}
```

#### FolderType.java
```java
public enum FolderType {
    INBOX,
    SENT,
    DRAFTS,
    TRASH,
    SPAM,
    CUSTOM
}
```

#### EmailStatus.java
```java
public enum EmailStatus {
    PENDING,
    SENT,
    FAILED,
    RECEIVED,
    PROCESSED,
    ERROR
}
```

### 3. Repository Layer

#### EmailMessageRepository.java
```java
@Repository
public interface EmailMessageRepository extends JpaRepository<EmailMessage, String> {
    Page<EmailMessage> findByIsDeletedFalseOrderByReceivedDateDesc(Pageable pageable);
    
    Page<EmailMessage> findByFoldersContainingAndIsDeletedFalse(EmailFolder folder, Pageable pageable);
    
    Page<EmailMessage> findByIsReadFalseAndIsDeletedFalse(Pageable pageable);
    
    Page<EmailMessage> findByIsFlaggedTrueAndIsDeletedFalse(Pageable pageable);
    
    Page<EmailMessage> findByIsImportantTrueAndIsDeletedFalse(Pageable pageable);
    
    @Query("SELECT e FROM EmailMessage e WHERE e.isDeleted = false AND " +
           "(LOWER(e.subject) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(e.from) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(e.textContent) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<EmailMessage> searchEmails(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    Optional<EmailMessage> findByImapUidAndImapFolder(Long imapUid, String imapFolder);
    
    long countByIsReadFalseAndIsDeletedFalse();
}
```

#### EmailFolderRepository.java
```java
@Repository
public interface EmailFolderRepository extends JpaRepository<EmailFolder, Long> {
    Optional<EmailFolder> findByName(String name);
    
    List<EmailFolder> findAllByOrderByDisplayOrderAsc();
    
    Optional<EmailFolder> findByType(FolderType type);
}
```

### 4. Service Layer

#### EmailSenderService.java
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailSenderService {
    private final JavaMailSender mailSender;
    private final EmailProperties properties;
    
    @Async
    public CompletableFuture<EmailMessage> sendEmail(EmailMessage emailMessage) {
        // Send email implementation
    }
    
    public EmailMessage sendEmailWithTemplate(String templateName, Map<String, Object> variables, String to) {
        // Template-based email sending
    }
    
    public EmailMessage sendEmailWithAttachments(EmailMessage emailMessage) {
        // Handle attachments
    }
}
```

#### EmailReceiverService.java
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailReceiverService {
    private final Store imapStore;
    private final EmailProperties properties;
    private final EmailMessageRepository messageRepository;
    private final EmailFolderRepository folderRepository;
    private final EmailEventPublisher eventPublisher;
    
    @Scheduled(fixedDelayString = "${email.imap.poll-interval}")
    public void pollEmails() {
        // Poll IMAP server for new emails
        // Mark new emails as unread by default
        // Save to database with proper folder assignment
    }
    
    public List<EmailMessage> fetchEmails(int maxMessages) {
        // Fetch emails from IMAP
        // Sync read/unread status with IMAP flags
        // Update local database
    }
    
    public EmailMessage fetchEmailById(String messageId) {
        // Fetch specific email from database first
        // If not found, fetch from IMAP
    }
    
    @EventListener
    public void handleNewEmail(NewEmailEvent event) {
        // Process new email
        // Auto-assign to INBOX folder
        // Apply spam/important detection rules
    }
    
    private void syncImapFlags(Message imapMessage, EmailMessage emailMessage) {
        // Sync SEEN, FLAGGED, DELETED flags between IMAP and local
    }
}
```

#### EmailMarkingService.java
```java
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class EmailMarkingService {
    private final EmailMessageRepository messageRepository;
    private final Store imapStore;
    
    public EmailMessage markAsRead(String messageId) {
        EmailMessage email = messageRepository.findById(messageId)
            .orElseThrow(() -> new EmailNotFoundException(messageId));
        
        email.setRead(true);
        email.setReadDate(LocalDateTime.now());
        
        // Update IMAP server
        updateImapFlag(email, Flags.Flag.SEEN, true);
        
        return messageRepository.save(email);
    }
    
    public EmailMessage markAsUnread(String messageId) {
        EmailMessage email = messageRepository.findById(messageId)
            .orElseThrow(() -> new EmailNotFoundException(messageId));
        
        email.setRead(false);
        email.setReadDate(null);
        
        // Update IMAP server
        updateImapFlag(email, Flags.Flag.SEEN, false);
        
        return messageRepository.save(email);
    }
    
    public EmailMessage toggleFlag(String messageId) {
        EmailMessage email = messageRepository.findById(messageId)
            .orElseThrow(() -> new EmailNotFoundException(messageId));
        
        email.setFlagged(!email.isFlagged());
        email.setFlaggedDate(email.isFlagged() ? LocalDateTime.now() : null);
        
        // Update IMAP server
        updateImapFlag(email, Flags.Flag.FLAGGED, email.isFlagged());
        
        return messageRepository.save(email);
    }
    
    public EmailMessage markAsImportant(String messageId, boolean important) {
        EmailMessage email = messageRepository.findById(messageId)
            .orElseThrow(() -> new EmailNotFoundException(messageId));
        
        email.setImportant(important);
        return messageRepository.save(email);
    }
    
    public EmailMessage markAsSpam(String messageId, boolean spam) {
        EmailMessage email = messageRepository.findById(messageId)
            .orElseThrow(() -> new EmailNotFoundException(messageId));
        
        email.setSpam(spam);
        
        // Move to spam folder if marked as spam
        if (spam) {
            EmailFolder spamFolder = folderRepository.findByType(FolderType.SPAM)
                .orElseThrow(() -> new FolderNotFoundException("SPAM"));
            email.getFolders().clear();
            email.getFolders().add(spamFolder);
        }
        
        return messageRepository.save(email);
    }
    
    public void markMultipleAsRead(List<String> messageIds) {
        messageRepository.findAllById(messageIds).forEach(email -> {
            email.setRead(true);
            email.setReadDate(LocalDateTime.now());
            updateImapFlag(email, Flags.Flag.SEEN, true);
        });
    }
    
    private void updateImapFlag(EmailMessage email, Flags.Flag flag, boolean set) {
        // Update flag on IMAP server
        try {
            Folder folder = imapStore.getFolder(email.getImapFolder());
            folder.open(Folder.READ_WRITE);
            Message message = folder.getMessageByUID(email.getImapUid());
            message.setFlag(flag, set);
            folder.close(false);
        } catch (Exception e) {
            log.error("Failed to update IMAP flag", e);
        }
    }
}
```

#### EmailFolderService.java
```java
@Service
@RequiredArgsConstructor
@Transactional
public class EmailFolderService {
    private final EmailFolderRepository folderRepository;
    private final EmailMessageRepository messageRepository;
    
    @PostConstruct
    public void initializeDefaultFolders() {
        createDefaultFolderIfNotExists("INBOX", "Inbox", "📥", FolderType.INBOX, 1);
        createDefaultFolderIfNotExists("SENT", "Sent", "📤", FolderType.SENT, 2);
        createDefaultFolderIfNotExists("DRAFTS", "Drafts", "📝", FolderType.DRAFTS, 3);
        createDefaultFolderIfNotExists("TRASH", "Trash", "🗑️", FolderType.TRASH, 4);
        createDefaultFolderIfNotExists("SPAM", "Spam", "🚫", FolderType.SPAM, 5);
    }
    
    private void createDefaultFolderIfNotExists(String name, String displayName, 
                                               String icon, FolderType type, int order) {
        if (folderRepository.findByName(name).isEmpty()) {
            EmailFolder folder = EmailFolder.builder()
                .name(name)
                .displayName(displayName)
                .icon(icon)
                .type(type)
                .displayOrder(order)
                .build();
            folderRepository.save(folder);
        }
    }
    
    public EmailFolder createCustomFolder(String name, String displayName, String icon) {
        EmailFolder folder = EmailFolder.builder()
            .name(name)
            .displayName(displayName)
            .icon(icon)
            .type(FolderType.CUSTOM)
            .displayOrder(100) // Custom folders after default ones
            .build();
        return folderRepository.save(folder);
    }
    
    public EmailMessage moveToFolder(String messageId, Long folderId) {
        EmailMessage email = messageRepository.findById(messageId)
            .orElseThrow(() -> new EmailNotFoundException(messageId));
        EmailFolder folder = folderRepository.findById(folderId)
            .orElseThrow(() -> new FolderNotFoundException(folderId));
        
        email.getFolders().clear();
        email.getFolders().add(folder);
        
        // Handle special folder logic
        if (folder.getType() == FolderType.TRASH) {
            email.setDeleted(true);
        } else if (folder.getType() == FolderType.SPAM) {
            email.setSpam(true);
        }
        
        return messageRepository.save(email);
    }
    
    public EmailMessage addLabel(String messageId, String label) {
        EmailMessage email = messageRepository.findById(messageId)
            .orElseThrow(() -> new EmailNotFoundException(messageId));
        
        email.getLabels().add(label);
        return messageRepository.save(email);
    }
    
    public EmailMessage removeLabel(String messageId, String label) {
        EmailMessage email = messageRepository.findById(messageId)
            .orElseThrow(() -> new EmailNotFoundException(messageId));
        
        email.getLabels().remove(label);
        return messageRepository.save(email);
    }
}
```

#### EmailTemplateService.java
```java
@Service
@RequiredArgsConstructor
public class EmailTemplateService {
    private final TemplateEngine templateEngine;
    
    public String processTemplate(String templateName, Map<String, Object> variables) {
        // Process Thymeleaf template
    }
    
    public boolean validateTemplate(String templateName) {
        // Validate template exists
    }
}
```

### 5. REST API Layer

#### EmailController.java
```java
@RestController
@RequestMapping("/api/v1/emails")
@RequiredArgsConstructor
@Slf4j
public class EmailController {
    private final EmailSenderService senderService;
    private final EmailReceiverService receiverService;
    private final EmailMarkingService markingService;
    private final EmailFolderService folderService;
    private final EmailMessageRepository messageRepository;
    
    // Sending endpoints
    @PostMapping("/send")
    public ResponseEntity<EmailResponse> sendEmail(@Valid @RequestBody EmailRequest request) {
        // Send email endpoint
    }
    
    @PostMapping("/send-template")
    public ResponseEntity<EmailResponse> sendTemplateEmail(@Valid @RequestBody TemplateEmailRequest request) {
        // Send template-based email
    }
    
    // Inbox and folder endpoints
    @GetMapping("/inbox")
    public ResponseEntity<Page<EmailMessage>> getInboxEmails(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String filter) {
        // Get inbox emails with filters (all, unread, flagged, important)
    }
    
    @GetMapping("/folder/{folderId}")
    public ResponseEntity<Page<EmailMessage>> getFolderEmails(
            @PathVariable Long folderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Get emails in specific folder
    }
    
    @GetMapping("/search")
    public ResponseEntity<Page<EmailMessage>> searchEmails(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Search emails
    }
    
    @GetMapping("/{messageId}")
    public ResponseEntity<EmailMessage> getEmail(@PathVariable String messageId) {
        // Get specific email and mark as read
    }
    
    // Marking endpoints
    @PutMapping("/{messageId}/mark-read")
    public ResponseEntity<EmailMessage> markAsRead(@PathVariable String messageId) {
        return ResponseEntity.ok(markingService.markAsRead(messageId));
    }
    
    @PutMapping("/{messageId}/mark-unread")
    public ResponseEntity<EmailMessage> markAsUnread(@PathVariable String messageId) {
        return ResponseEntity.ok(markingService.markAsUnread(messageId));
    }
    
    @PutMapping("/{messageId}/toggle-flag")
    public ResponseEntity<EmailMessage> toggleFlag(@PathVariable String messageId) {
        return ResponseEntity.ok(markingService.toggleFlag(messageId));
    }
    
    @PutMapping("/{messageId}/mark-important")
    public ResponseEntity<EmailMessage> markAsImportant(
            @PathVariable String messageId,
            @RequestParam boolean important) {
        return ResponseEntity.ok(markingService.markAsImportant(messageId, important));
    }
    
    @PutMapping("/{messageId}/mark-spam")
    public ResponseEntity<EmailMessage> markAsSpam(
            @PathVariable String messageId,
            @RequestParam boolean spam) {
        return ResponseEntity.ok(markingService.markAsSpam(messageId, spam));
    }
    
    // Bulk operations
    @PutMapping("/bulk/mark-read")
    public ResponseEntity<Void> markMultipleAsRead(@RequestBody List<String> messageIds) {
        markingService.markMultipleAsRead(messageIds);
        return ResponseEntity.ok().build();
    }
    
    // Folder management
    @GetMapping("/folders")
    public ResponseEntity<List<EmailFolder>> getFolders() {
        return ResponseEntity.ok(folderService.getAllFolders());
    }
    
    @PostMapping("/folders")
    public ResponseEntity<EmailFolder> createFolder(@Valid @RequestBody CreateFolderRequest request) {
        return ResponseEntity.ok(folderService.createCustomFolder(
            request.getName(), request.getDisplayName(), request.getIcon()));
    }
    
    @PutMapping("/{messageId}/move-to-folder/{folderId}")
    public ResponseEntity<EmailMessage> moveToFolder(
            @PathVariable String messageId,
            @PathVariable Long folderId) {
        return ResponseEntity.ok(folderService.moveToFolder(messageId, folderId));
    }
    
    // Labels
    @PutMapping("/{messageId}/labels/{label}")
    public ResponseEntity<EmailMessage> addLabel(
            @PathVariable String messageId,
            @PathVariable String label) {
        return ResponseEntity.ok(folderService.addLabel(messageId, label));
    }
    
    @DeleteMapping("/{messageId}/labels/{label}")
    public ResponseEntity<EmailMessage> removeLabel(
            @PathVariable String messageId,
            @PathVariable String label) {
        return ResponseEntity.ok(folderService.removeLabel(messageId, label));
    }
    
    // Statistics
    @GetMapping("/stats/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount() {
        long count = messageRepository.countByIsReadFalseAndIsDeletedFalse();
        return ResponseEntity.ok(new UnreadCountResponse(count));
    }
}
```

### 6. DTOs and Requests

#### EmailRequest.java
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailRequest {
    @NotBlank
    @Email
    private String to;
    
    @Email
    private String cc;
    
    @Email
    private String bcc;
    
    @NotBlank
    private String subject;
    
    private String textContent;
    private String htmlContent;
    
    private List<AttachmentRequest> attachments;
}
```

#### TemplateEmailRequest.java
```java
@Data
public class TemplateEmailRequest {
    @NotBlank
    @Email
    private String to;
    
    @NotBlank
    private String templateName;
    
    private Map<String, Object> templateVariables;
    
    private String subject;
}
```

#### CreateFolderRequest.java
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateFolderRequest {
    @NotBlank
    private String name;
    
    @NotBlank
    private String displayName;
    
    private String icon = "📁";
}
```

#### UnreadCountResponse.java
```java
@Data
@AllArgsConstructor
public class UnreadCountResponse {
    private long unreadCount;
}
```

### 7. Event System

#### NewEmailEvent.java
```java
@Getter
@AllArgsConstructor
public class NewEmailEvent {
    private final EmailMessage emailMessage;
    private final LocalDateTime timestamp;
}
```

#### EmailEventPublisher.java
```java
@Component
@RequiredArgsConstructor
public class EmailEventPublisher {
    private final ApplicationEventPublisher eventPublisher;
    
    public void publishNewEmailEvent(EmailMessage email) {
        eventPublisher.publishEvent(new NewEmailEvent(email, LocalDateTime.now()));
    }
}
```

## Application Properties Configuration

```properties
# Email Configuration
email.smtp.host=smtp.gmail.com
email.smtp.port=587
email.smtp.username=${SMTP_USERNAME}
email.smtp.password=${SMTP_PASSWORD}
email.smtp.auth=true
email.smtp.starttls=true
email.smtp.connection-timeout=5000
email.smtp.timeout=5000

email.imap.host=imap.gmail.com
email.imap.port=993
email.imap.username=${IMAP_USERNAME}
email.imap.password=${IMAP_PASSWORD}
email.imap.folder=INBOX
email.imap.ssl=true
email.imap.poll-interval=60000

email.general.from-address=noreply@example.com
email.general.from-name=Email Service
email.general.debug-enabled=false
email.general.max-retries=3

# Database Configuration (H2 for development)
spring.datasource.url=jdbc:h2:file:./data/emaildb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=update
spring.h2.console.enabled=true

# Async Configuration
spring.task.execution.pool.core-size=2
spring.task.execution.pool.max-size=5
spring.task.execution.pool.queue-capacity=100
```

## Security Considerations

1. **Credentials Management**
   - Use environment variables for sensitive data
   - Consider using Spring Cloud Config or Vault
   - Never commit credentials to version control

2. **Email Validation**
   - Validate all email addresses
   - Sanitize HTML content to prevent XSS
   - Limit attachment sizes

3. **Rate Limiting**
   - Implement rate limiting for API endpoints
   - Add throttling for IMAP polling

## Error Handling

```java
@RestControllerAdvice
public class EmailExceptionHandler {
    
    @ExceptionHandler(MailException.class)
    public ResponseEntity<ErrorResponse> handleMailException(MailException e) {
        // Handle mail sending errors
    }
    
    @ExceptionHandler(MessagingException.class)
    public ResponseEntity<ErrorResponse> handleMessagingException(MessagingException e) {
        // Handle email protocol errors
    }
}
```

## Testing Strategy

1. **Unit Tests**
   - Mock JavaMailSender for sender service tests
   - Mock IMAP Store for receiver service tests
   - Test email validation logic

2. **Integration Tests**
   - Use GreenMail for email server simulation
   - Test full email send/receive flow
   - Verify template processing

3. **End-to-End Tests**
   - Test REST API endpoints
   - Verify async operations
   - Test error scenarios

## Implementation Dependencies

Add to pom.xml:
```xml
<dependencies>
    <!-- Spring Boot Mail -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-mail</artifactId>
    </dependency>
    
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- Spring Boot Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    
    <!-- H2 Database -->
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>
    
    <!-- Spring Boot Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <!-- Thymeleaf for email templates -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>
    
    <!-- Configuration Properties -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-configuration-processor</artifactId>
        <optional>true</optional>
    </dependency>
    
    <!-- JavaMail Extensions for IMAP -->
    <dependency>
        <groupId>com.sun.mail</groupId>
        <artifactId>javax.mail</artifactId>
        <version>1.6.2</version>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>com.icegreen</groupId>
        <artifactId>greenmail-spring</artifactId>
        <version>2.0.1</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Email Client Features Summary

The enhanced design now includes:

1. **Email Marking System**
   - Read/Unread status with timestamps
   - Flag/Star functionality
   - Important marking
   - Spam detection
   - Soft delete (move to trash)

2. **Folder Organization**
   - Default folders: Inbox, Sent, Drafts, Trash, Spam
   - Custom folder creation
   - Move emails between folders
   - Folder-based email filtering

3. **Label System**
   - Add/remove custom labels
   - Multiple labels per email
   - Label-based filtering

4. **Database Persistence**
   - JPA entities for emails, attachments, and folders
   - H2 database for development
   - Automatic folder initialization
   - Email metadata tracking

5. **IMAP Synchronization**
   - Sync read/unread status with IMAP server
   - Sync flagged status
   - Preserve IMAP UIDs for tracking

6. **REST API Endpoints**
   - Mark read/unread: `PUT /api/v1/emails/{id}/mark-read`
   - Toggle flag: `PUT /api/v1/emails/{id}/toggle-flag`
   - Mark important: `PUT /api/v1/emails/{id}/mark-important`
   - Mark spam: `PUT /api/v1/emails/{id}/mark-spam`
   - Bulk operations: `PUT /api/v1/emails/bulk/mark-read`
   - Folder management: `GET/POST /api/v1/emails/folders`
   - Move to folder: `PUT /api/v1/emails/{id}/move-to-folder/{folderId}`
   - Label management: `PUT/DELETE /api/v1/emails/{id}/labels/{label}`
   - Unread count: `GET /api/v1/emails/stats/unread-count`

## Next Steps

1. Implement the configuration classes
2. Create JPA entities and repositories
3. Implement service layer components (marking, folder, receiver services)
4. Implement REST API endpoints
5. Add email templates
6. Create comprehensive tests
7. Add monitoring and metrics
8. Document API with OpenAPI/Swagger