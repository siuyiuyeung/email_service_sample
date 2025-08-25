# Email Service Design Documentation

## System Architecture Overview

The Email Service is a Spring Boot application that demonstrates email operations using JavaMail API with support for:
- Sending emails via SMTP
- Receiving emails via IMAP
- RESTful API for email operations
- Asynchronous email processing
- Email template support

```
┌─────────────────────────────────────────────────────────────┐
│                    Email Service Application                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌────────────┐ │
│  │   REST API      │  │  Email Listener  │  │  Scheduler │ │
│  │  Controller     │  │   Component      │  │  Component │ │
│  └────────┬────────┘  └────────┬─────────┘  └─────┬──────┘ │
│           │                    │                   │        │
│  ┌────────▼────────────────────▼───────────────────▼──────┐ │
│  │                  Service Layer                          │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │ │
│  │  │ Email Sender │  │Email Receiver│  │Email Template│ │ │
│  │  │   Service    │  │   Service    │  │   Service    │ │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘ │ │
│  └─────────────────────────┬──────────────────────────────┘ │
│                            │                                │
│  ┌─────────────────────────▼──────────────────────────────┐ │
│  │                  Email Configuration                    │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │ │
│  │  │ SMTP Config  │  │ IMAP Config  │  │ Mail Session │ │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘ │ │
│  └─────────────────────────────────────────────────────────┘ │
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
public class EmailMessage {
    private String messageId;
    private String from;
    private List<String> to;
    private List<String> cc;
    private List<String> bcc;
    private String subject;
    private String textContent;
    private String htmlContent;
    private LocalDateTime sentDate;
    private LocalDateTime receivedDate;
    private List<EmailAttachment> attachments;
    private Map<String, String> headers;
    private EmailStatus status;
    private String errorMessage;
}
```

#### EmailAttachment.java
```java
@Data
@Builder
public class EmailAttachment {
    private String filename;
    private String contentType;
    private byte[] content;
    private long size;
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

### 3. Service Layer

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
    
    @Scheduled(fixedDelayString = "${email.imap.poll-interval}")
    public void pollEmails() {
        // Poll IMAP server for new emails
    }
    
    public List<EmailMessage> fetchEmails(int maxMessages) {
        // Fetch emails from IMAP
    }
    
    public EmailMessage fetchEmailById(String messageId) {
        // Fetch specific email
    }
    
    @EventListener
    public void handleNewEmail(NewEmailEvent event) {
        // Process new email
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

### 4. REST API Layer

#### EmailController.java
```java
@RestController
@RequestMapping("/api/v1/emails")
@RequiredArgsConstructor
@Slf4j
public class EmailController {
    private final EmailSenderService senderService;
    private final EmailReceiverService receiverService;
    
    @PostMapping("/send")
    public ResponseEntity<EmailResponse> sendEmail(@Valid @RequestBody EmailRequest request) {
        // Send email endpoint
    }
    
    @PostMapping("/send-template")
    public ResponseEntity<EmailResponse> sendTemplateEmail(@Valid @RequestBody TemplateEmailRequest request) {
        // Send template-based email
    }
    
    @GetMapping("/inbox")
    public ResponseEntity<List<EmailMessage>> getInboxEmails(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        // Get inbox emails
    }
    
    @GetMapping("/{messageId}")
    public ResponseEntity<EmailMessage> getEmail(@PathVariable String messageId) {
        // Get specific email
    }
}
```

### 5. DTOs and Requests

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

### 6. Event System

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
    
    <!-- Testing -->
    <dependency>
        <groupId>com.icegreen</groupId>
        <artifactId>greenmail-spring</artifactId>
        <version>2.0.1</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Next Steps

1. Implement the configuration classes
2. Create the service layer components
3. Implement REST API endpoints
4. Add email templates
5. Create comprehensive tests
6. Add monitoring and metrics
7. Document API with OpenAPI/Swagger