# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a fully implemented Spring Boot email service that demonstrates how to send emails via SMTP and receive emails via IMAP. The application includes advanced features like email marking (read/unread, flagged, important), folder management, label system, duplicate prevention for multi-instance deployments, and optional S/MIME support for email encryption and digital signatures.

## Key Features

- **Email Sending**: SMTP-based email sending with template support and automatic sent folder storage
- **Email Receiving**: IMAP polling with automatic synchronization and batch processing
- **Email Management**: Mark emails as read/unread, flagged, important, spam
- **Folder System**: Organize emails in folders (Inbox, Sent, Drafts, Trash, Spam, Custom)
- **Sent Email Storage**: Automatically saves sent emails to Sent folder for complete email history
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
   - `EmailMessage`: Main email entity with auto-generated ID and unique messageId
   - `EmailFolder`: Folder organization entity
   - `EmailAttachment`: Email attachment handling
   - `Certificate`: S/MIME certificate management
   - `EmailSyncState`: IMAP synchronization tracking with UID tracking

3. **Service Layer** (`service` package)
   - `EmailSenderService`: SMTP email sending with templates
   - `EmailReceiverService`: IMAP email synchronization logic
   - `EmailMarkingService`: Email marking operations
   - `EmailFolderService`: Folder and label management
   - `DistributedLockService`: Duplicate prevention locking
   - `EmailDTOMapper`: Entity to DTO conversion service
   - `SMimeService`: S/MIME encryption, signing, decryption, and certificate management

4. **REST API Layer** (`controller` package)
   - `EmailController`: REST endpoints for all email operations
   - `EmailPollingController`: REST endpoints for manual polling control
   - `LockManagementController`: REST endpoints for lock management
   - `SmimeCertificateController`: REST endpoints for S/MIME certificate management
   - `EmailExceptionHandler`: Global exception handling

5. **Scheduler Layer** (`scheduler` package)
   - `EmailPollingScheduler`: Handles scheduled email polling
   - `LockCleanupScheduler`: Handles scheduled lock cleanup
   - `CertificateValidationScheduler`: Daily job to mark expired S/MIME certificates
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
- `POST /api/v1/emails/send` - Send email (automatically saved to Sent folder)
- `POST /api/v1/emails/send-template` - Send templated email
- `GET /api/v1/emails/inbox` - Get inbox emails with pagination
- `GET /api/v1/emails/sent` - Get sent emails with pagination
- `GET /api/v1/emails/{id}` - Get specific email (uses auto-generated ID)
- `GET /api/v1/emails/search?query=` - Search emails

### Email Marking
- `PUT /api/v1/emails/{id}/mark-read` - Mark as read
- `PUT /api/v1/emails/{id}/mark-unread` - Mark as unread
- `PUT /api/v1/emails/{id}/toggle-flag` - Toggle flag
- `PUT /api/v1/emails/{id}/mark-important` - Mark as important
- `PUT /api/v1/emails/{id}/mark-spam` - Mark as spam
- `PUT /api/v1/emails/bulk/mark-read` - Bulk mark as read (accepts array of IDs)

### Folder Management
- `GET /api/v1/emails/folders` - Get all folders
- `POST /api/v1/emails/folders` - Create custom folder
- `GET /api/v1/emails/folder/{folderId}` - Get emails in folder
- `PUT /api/v1/emails/{id}/move-to-folder/{folderId}` - Move to folder

### Labels
- `PUT /api/v1/emails/{id}/labels/{label}` - Add label
- `DELETE /api/v1/emails/{id}/labels/{label}` - Remove label

### Statistics
- `GET /api/v1/emails/stats/unread-count` - Get unread count

### Email Polling Control
- `POST /api/v1/email-polling/trigger` - Manually trigger email synchronization
- `GET /api/v1/email-polling/status` - Get current polling status
- `GET /api/v1/email-polling/progress` - Get sync progress for all folders
- `GET /api/v1/email-polling/progress/{folderName}` - Get sync progress for specific folder

### Lock Management
- `POST /api/v1/locks/cleanup` - Manually trigger cleanup of expired locks

### S/MIME Certificate Management
- `GET /api/v1/smime/certificates` - List all stored certificates
- `GET /api/v1/smime/certificates/{id}` - Get a specific certificate
- `POST /api/v1/smime/certificates` - Import a PEM-encoded certificate
- `PUT /api/v1/smime/certificates/{id}/trust?trusted=true` - Set trusted flag
- `DELETE /api/v1/smime/certificates/{id}` - Delete a certificate
- `POST /api/v1/smime/certificates/validate` - Trigger expiry check on all certificates

## Development Guidelines

### Java 8 Compatibility
The project uses Java 8. Ensure compatibility:
- Avoid using `var` keyword
- Use `ByteArrayOutputStream` + read loop instead of `InputStream.readAllBytes()`
- Use traditional lambda syntax
- `MimeBodyPart` has no `saveChanges()` — only `MimeMessage` does
- When chaining Bouncy Castle builder methods (e.g. `setProvider()`), be aware some return the parent type — split declaration and chained call onto separate lines

### Database Schema
- EmailMessage uses auto-generated Long ID as primary key
- messageId remains a unique business identifier (VARCHAR(1000) to support long message IDs)
- Email fields support larger sizes: subject (1000), from (500), imapFolder (500)
- Proper indexes on messageId and imapUid/imapFolder for performance

### Service Implementation
- Services are in `com.igsl.group.email_service_sample.service`
- Use `@Service`, `@Transactional`, and `@Async` appropriately
- Implement proper error handling and logging
- Batch processing: Each sync job processes up to batch-size messages (default 100) then stops
- Progressive sync: Remaining messages are processed in subsequent scheduled jobs
- Chronological order: Messages are processed oldest first to maintain proper sequence
- Progress tracking: Sync state is updated after every message for precise resumption capability
- UID tracking: Highest UID seen is tracked for efficient IMAP synchronization
- `@Transactional` on `private` methods is never applied by Spring AOP — use it only on public/protected methods
- Self-invocation (`this.method()`) bypasses Spring's transaction proxy — avoid `@Transactional` on methods called internally from within the same bean

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

## S/MIME Implementation Notes

S/MIME is fully implemented and disabled by default (`email.smime.enabled=false`).

### How S/MIME works in this service
- **Outgoing**: `EmailSenderService` calls `smimeService.prepareOutgoing()` after building the `MimeMessage`. It signs first (if a private key is loaded), then encrypts for each recipient that has a stored certificate.
- **Incoming**: `EmailReceiverService` calls `smimeService.processIncoming()` before extracting text/HTML content. This decrypts the message first (if encrypted), then verifies the signature. The decrypted `MimeMessage` is used for content extraction so the stored text/HTML is always plaintext.
- S/MIME status is stored on `EmailMessage` (fields: `isSigned`, `isEncrypted`, `signatureValid`, `signerEmail`, `signerCertificateThumbprint`, `smimeStatus`, `smimeErrors`).

### Certificate storage
- `Certificate` entity in the `certificates` table — one record per email address (unique constraint).
- `persistCertificate()` uses an update-in-place strategy: checks by thumbprint first, then falls back to finding by email address and updating the existing record to avoid unique-constraint violations.
- Signer certificates are auto-discovered from signed received emails when `email.smime.auto-download-certificates=true`.
- Own certificate is imported into the DB automatically on startup.

### Enabling S/MIME
1. Generate or obtain a PKCS12 keystore containing your private key and certificate.
2. Set environment variables `SMIME_KEYSTORE_PATH` and `SMIME_KEYSTORE_PASSWORD`.
3. Set `email.smime.enabled=true` in `application.properties`.
4. Import recipient public certificates via `POST /api/v1/smime/certificates` before sending encrypted mail.

## Troubleshooting

### Common Issues
1. **Email not sending**: Check SMTP credentials and firewall settings
2. **IMAP not connecting**: Verify IMAP settings and SSL/TLS configuration
3. **Duplicate emails**: Ensure distributed lock service is working
4. **H2 database locked**: Stop all application instances before accessing H2 console
5. **S/MIME signing fails on startup**: Check `SMIME_KEYSTORE_PATH` exists and `SMIME_KEYSTORE_PASSWORD` is correct; the service degrades gracefully (warning logged, emails still send unsigned)
6. **S/MIME encryption fails**: No certificate is stored for the recipient — import their public certificate first via `POST /api/v1/smime/certificates`; if `email.smime.require-encryption=false` (default) the email is sent signed-only
7. **Decryption failed status on received email**: The private key in the keystore does not match the encryption recipient — verify the correct keystore is configured

### Debug Mode
Enable debug logging by setting in `application.properties`:
```properties
email.general.debug-enabled=true
logging.level.org.springframework.mail=DEBUG
logging.level.javax.mail=DEBUG
```