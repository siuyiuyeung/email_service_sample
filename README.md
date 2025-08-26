# Email Service Sample

A comprehensive Spring Boot application that demonstrates email operations using JavaMail API with support for both SMTP (sending) and IMAP (receiving) protocols.

## Features

- **Email Sending** via SMTP
- **Email Receiving** via IMAP with automatic polling
- **Email Client Features**: Read/Unread, Flagged, Important, Spam marking
- **Folder Management**: Default folders (Inbox, Sent, Drafts, Trash, Spam) and custom folders
- **Label System**: Add custom labels to emails
- **Search Functionality**: Search emails by subject, from, or content
- **Template Support**: Send emails using Thymeleaf templates
- **Duplicate Prevention**: Distributed locking for multi-instance deployments
- **S/MIME Support** (Optional): Email encryption and digital signatures
- **RESTful API**: Complete REST API for all email operations
- **H2 Database**: Persistent storage of emails and metadata

## Prerequisites

- Java 8 or higher
- Maven 3.6 or higher
- Email account with SMTP/IMAP access (e.g., Gmail with app-specific password)

## Quick Start

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd email_service_sample
   ```

2. **Configure email credentials**
   
   Set environment variables:
   ```bash
   export SMTP_USERNAME=your-email@gmail.com
   export SMTP_PASSWORD=your-app-password
   export IMAP_USERNAME=your-email@gmail.com
   export IMAP_PASSWORD=your-app-password
   ```

   Or modify `application.properties` directly.

3. **Build and run**
   ```bash
   # Windows
   ./mvnw.cmd spring-boot:run

   # Unix/Mac
   ./mvnw spring-boot:run
   ```

4. **Access the application**
   - REST API: http://localhost:8080/api/v1/emails
   - H2 Console: http://localhost:8080/h2-console (username: sa, no password)

## API Endpoints

### Email Sending
- `POST /api/v1/emails/send` - Send an email
- `POST /api/v1/emails/send-template` - Send email using template

### Email Reading
- `GET /api/v1/emails/inbox` - Get inbox emails (with pagination and filters)
- `GET /api/v1/emails/{messageId}` - Get specific email
- `GET /api/v1/emails/search?query={term}` - Search emails

### Email Marking
- `PUT /api/v1/emails/{messageId}/mark-read` - Mark as read
- `PUT /api/v1/emails/{messageId}/mark-unread` - Mark as unread
- `PUT /api/v1/emails/{messageId}/toggle-flag` - Toggle flag/star
- `PUT /api/v1/emails/{messageId}/mark-important?important=true` - Mark as important
- `PUT /api/v1/emails/{messageId}/mark-spam?spam=true` - Mark as spam
- `PUT /api/v1/emails/bulk/mark-read` - Mark multiple as read

### Folder Management
- `GET /api/v1/emails/folders` - Get all folders
- `POST /api/v1/emails/folders` - Create custom folder
- `PUT /api/v1/emails/{messageId}/move-to-folder/{folderId}` - Move to folder

### Labels
- `PUT /api/v1/emails/{messageId}/labels/{label}` - Add label
- `DELETE /api/v1/emails/{messageId}/labels/{label}` - Remove label

### Statistics
- `GET /api/v1/emails/stats/unread-count` - Get unread email count

## Configuration

### Gmail Setup

1. Enable 2-factor authentication in your Google account
2. Generate an app-specific password:
   - Go to https://myaccount.google.com/security
   - Select "2-Step Verification" → "App passwords"
   - Generate a password for "Mail"
3. Enable IMAP access:
   - Gmail Settings → Forwarding and POP/IMAP → Enable IMAP

### Application Properties

Key configuration in `application.properties`:

```properties
# SMTP Configuration
email.smtp.host=smtp.gmail.com
email.smtp.port=587
email.smtp.username=your-email@gmail.com
email.smtp.password=your-app-password

# IMAP Configuration
email.imap.host=imap.gmail.com
email.imap.port=993
email.imap.username=your-email@gmail.com
email.imap.password=your-app-password
email.imap.poll-interval=60000  # Poll every 60 seconds

# S/MIME (Optional)
email.smime.enabled=false
```

## Example API Calls

### Send Email
```bash
curl -X POST http://localhost:8080/api/v1/emails/send \
  -H "Content-Type: application/json" \
  -d '{
    "to": "recipient@example.com",
    "subject": "Test Email",
    "textContent": "This is a test email",
    "htmlContent": "<h1>This is a test email</h1>"
  }'
```

### Get Inbox (Unread Only)
```bash
curl http://localhost:8080/api/v1/emails/inbox?filter=unread&page=0&size=20
```

### Mark as Read
```bash
curl -X PUT http://localhost:8080/api/v1/emails/{messageId}/mark-read
```

### Search Emails
```bash
curl http://localhost:8080/api/v1/emails/search?query=invoice
```

## Development

### Project Structure
```
src/main/java/com/igsl/group/email_service_sample/
├── config/          # Configuration classes
├── controller/      # REST controllers
├── dto/            # Data transfer objects
├── event/          # Event handling
├── exception/      # Custom exceptions
├── model/          # JPA entities
├── repository/     # Data repositories
└── service/        # Business logic
```

### Running Tests
```bash
./mvnw test
```

### Building
```bash
./mvnw clean package
```

## Advanced Features

### Multi-Instance Deployment
The application includes distributed locking to prevent duplicate email synchronization when running multiple instances.

### S/MIME Support
Enable S/MIME in `application.properties` and provide certificates for email encryption and digital signatures.

### Email Templates
Place Thymeleaf templates in `src/main/resources/templates/` and use the template endpoint to send formatted emails.

## Troubleshooting

1. **Connection Issues**: Check firewall settings and ensure SMTP/IMAP ports are accessible
2. **Authentication Failures**: Verify app-specific passwords and account settings
3. **SSL/TLS Errors**: Ensure proper certificates or set appropriate trust settings
4. **Database Issues**: Check H2 database file permissions in `./data/` directory

## License

This project is a sample application for educational purposes.