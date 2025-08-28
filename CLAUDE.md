# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a fully implemented Spring Boot email service that demonstrates how to send emails via SMTP and receive emails via IMAP. The application includes advanced features like email marking (read/unread, flagged, important), folder management, label system, duplicate prevention for multi-instance deployments, and optional S/MIME support for email encryption and digital signatures.

## Key Features

- **Email Sending**: SMTP-based email sending with template support
- **Email Receiving**: IMAP polling with automatic synchronization and batch processing
- **Email Management**: Mark emails as read/unread, flagged, important, spam
- **Folder System**: Organize emails in folders (Inbox, Sent, Drafts, Trash, Spam, Custom)
- **Label System**: Add custom labels to emails for organization
- **Duplicate Prevention**: Distributed locking mechanism for multi-instance deployments
- **Batch Processing**: Configurable batch size for email synchronization with progress tracking
- **S/MIME Support**: Optional encryption and digital signature capabilities
- **REST API**: Full REST API for email operations
- **Async Processing**: Asynchronous email sending and scheduled polling
- **Database Storage**: H2 database for email persistence

## Architecture Overview

### Core Components

1. **Configuration Layer** (`config` package)
   - `EmailProperties`: Configuration properties for SMTP/IMAP
   - `EmailConfiguration`: Spring beans for mail services
   - `SecurityConfiguration`: Security settings for S/MIME

2. **Model Layer** (`model` package)
   - `EmailMessage`: Main email entity with marking properties
   - `EmailFolder`: Folder organization entity
   - `EmailAttachment`: Email attachment handling
   - `EmailCertificate`: S/MIME certificate management
   - `EmailSyncState`: IMAP synchronization tracking

3. **Service Layer** (`service` package)
   - `EmailSenderService`: SMTP email sending with templates
   - `EmailReceiverService`: IMAP email synchronization logic
   - `EmailMarkingService`: Email marking operations
   - `EmailFolderService`: Folder and label management
   - `DistributedLockService`: Duplicate prevention locking
   - `EmailDTOMapper`: Entity to DTO conversion service
   - `SMimeService`: S/MIME encryption/signing (structure ready)

4. **REST API Layer** (`controller` package)
   - `EmailController`: REST endpoints for all email operations
   - `EmailPollingController`: REST endpoints for manual polling control
   - `LockManagementController`: REST endpoints for lock management
   - `EmailExceptionHandler`: Global exception handling

5. **Scheduler Layer** (`scheduler` package)
   - `EmailPollingScheduler`: Handles scheduled email polling
   - `LockCleanupScheduler`: Handles scheduled lock cleanup
   - `SchedulerConfiguration`: Enables Spring scheduling

6. **Event System** (`event` package)
   - `EmailEventPublisher`: Publishes email events
   - `NewEmailEvent`: Event for new email arrivals

## Build and Run Commands

### Build the project
```bash
# Windows
./mvnw.cmd clean package

# Unix/Mac
./mvnw clean package
```

### Run the application
```bash
# Windows
./mvnw.cmd spring-boot:run

# Unix/Mac
./mvnw spring-boot:run
```

### Run tests
```bash
# Windows
./mvnw.cmd test

# Unix/Mac
./mvnw test
```

### Run a single test class
```bash
# Windows
./mvnw.cmd test -Dtest=EmailServiceSampleApplicationTests

# Unix/Mac
./mvnw test -Dtest=EmailServiceSampleApplicationTests
```

## Database Access

The application uses H2 database. Access the H2 console at:
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:file:./data/emaildb`
- Username: `sa`
- Password: (empty)

## Configuration

Edit `src/main/resources/application.properties` to configure:
- SMTP settings (host, port, credentials)
- IMAP settings (host, port, credentials, poll interval, batch size)
- S/MIME settings (certificates, algorithms)
- Database settings
- Batch processing settings

### Environment Variables
- `SMTP_USERNAME`: SMTP server username
- `SMTP_PASSWORD`: SMTP server password
- `IMAP_USERNAME`: IMAP server username
- `IMAP_PASSWORD`: IMAP server password
- `SMIME_KEYSTORE_PATH`: Path to S/MIME keystore
- `SMIME_KEYSTORE_PASSWORD`: Keystore password

### Batch Processing Configuration
- `email.imap.batch-size`: Maximum number of emails to process per job execution (default: 100)
- Each scheduled job processes up to batch-size messages then stops
- Remaining messages are processed in subsequent job runs
- Messages are processed in chronological order (oldest first)
- Progress is saved after every message for precise resumption capability
- Memory-efficient processing prevents long-running jobs

## API Endpoints

### Email Operations
- `POST /api/v1/emails/send` - Send email
- `POST /api/v1/emails/send-template` - Send templated email
- `GET /api/v1/emails/inbox` - Get inbox emails with pagination
- `GET /api/v1/emails/{messageId}` - Get specific email
- `GET /api/v1/emails/search?query=` - Search emails

### Email Marking
- `PUT /api/v1/emails/{messageId}/mark-read` - Mark as read
- `PUT /api/v1/emails/{messageId}/mark-unread` - Mark as unread
- `PUT /api/v1/emails/{messageId}/toggle-flag` - Toggle flag
- `PUT /api/v1/emails/{messageId}/mark-important` - Mark as important
- `PUT /api/v1/emails/{messageId}/mark-spam` - Mark as spam
- `PUT /api/v1/emails/bulk/mark-read` - Bulk mark as read

### Folder Management
- `GET /api/v1/emails/folders` - Get all folders
- `POST /api/v1/emails/folders` - Create custom folder
- `GET /api/v1/emails/folder/{folderId}` - Get emails in folder
- `PUT /api/v1/emails/{messageId}/move-to-folder/{folderId}` - Move to folder

### Labels
- `PUT /api/v1/emails/{messageId}/labels/{label}` - Add label
- `DELETE /api/v1/emails/{messageId}/labels/{label}` - Remove label

### Statistics
- `GET /api/v1/emails/stats/unread-count` - Get unread count

### Email Polling Control
- `POST /api/v1/email-polling/trigger` - Manually trigger email synchronization
- `GET /api/v1/email-polling/status` - Get current polling status
- `GET /api/v1/email-polling/progress` - Get sync progress for all folders
- `GET /api/v1/email-polling/progress/{folderName}` - Get sync progress for specific folder

### Lock Management
- `POST /api/v1/locks/cleanup` - Manually trigger cleanup of expired locks

## Development Guidelines

### Java 8 Compatibility
The project uses Java 8. Ensure compatibility:
- Avoid using `var` keyword
- Use ByteArrayOutputStream instead of `InputStream.readAllBytes()`
- Use traditional lambda syntax

### Service Implementation
- Services are in `com.igsl.group.email_service_sample.service`
- Use `@Service`, `@Transactional`, and `@Async` appropriately
- Implement proper error handling and logging
- Batch processing: Each sync job processes up to batch-size messages (default 100) then stops
- Progressive sync: Remaining messages are processed in subsequent scheduled jobs
- Chronological order: Messages are processed oldest first to maintain proper sequence
- Progress tracking: Sync state is updated after every message for precise resumption capability

### Repository Pattern
- Repositories extend `JpaRepository`
- Custom queries use `@Query` annotation
- Follow Spring Data JPA naming conventions

### REST API Design
- Use proper HTTP methods and status codes
- Implement request validation with `@Valid`
- Use DTOs for request/response mapping

### Testing
- Use `@SpringBootTest` for integration tests
- Mock external dependencies with `@MockBean`
- Test naming convention: `*Tests.java`

## Pending Implementation

### S/MIME Service
The S/MIME service structure is in place but needs full implementation:
1. Certificate management operations
2. Email signing logic
3. Email encryption/decryption logic
4. Certificate validation and trust chain

To complete S/MIME:
- Implement `SMimeService` methods
- Add Bouncy Castle provider initialization
- Create certificate storage mechanism
- Implement certificate discovery from emails

## Troubleshooting

### Common Issues
1. **Email not sending**: Check SMTP credentials and firewall settings
2. **IMAP not connecting**: Verify IMAP settings and SSL/TLS configuration
3. **Duplicate emails**: Ensure distributed lock service is working
4. **H2 database locked**: Stop all application instances before accessing H2 console

### Debug Mode
Enable debug logging by setting in `application.properties`:
```properties
email.general.debug-enabled=true
logging.level.org.springframework.mail=DEBUG
logging.level.javax.mail=DEBUG
```