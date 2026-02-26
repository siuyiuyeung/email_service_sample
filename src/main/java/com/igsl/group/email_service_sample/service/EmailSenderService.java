package com.igsl.group.email_service_sample.service;

import com.igsl.group.email_service_sample.config.EmailProperties;
import com.igsl.group.email_service_sample.model.EmailAttachment;
import com.igsl.group.email_service_sample.model.EmailFolder;
import com.igsl.group.email_service_sample.model.EmailMessage;
import com.igsl.group.email_service_sample.model.EmailStatus;
import com.igsl.group.email_service_sample.model.FolderType;
import com.igsl.group.email_service_sample.model.SmimeStatus;
import com.igsl.group.email_service_sample.repository.EmailFolderRepository;
import com.igsl.group.email_service_sample.repository.EmailMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.mail.*;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailSenderService {
    private final JavaMailSender mailSender;
    private final EmailProperties properties;
    private final EmailTemplateService templateService;
    private final EmailMessageRepository messageRepository;
    private final EmailFolderRepository folderRepository;
    private final Store imapStore;
    private final SMimeService smimeService;
    
    @Async("emailTaskExecutor")
    @Transactional
    public CompletableFuture<EmailMessage> sendEmail(EmailMessage emailMessage) {
        try {
            // Set from address if not provided
            if (emailMessage.getFrom() == null) {
                emailMessage.setFrom(properties.getGeneral().getFromAddress());
            }

            MimeMessage message = createMimeMessage(emailMessage);

            // Apply S/MIME signing/encryption when enabled
            if (properties.getSmime().isEnabled()) {
                try {
                    message = smimeService.prepareOutgoing(message, emailMessage.getTo());
                    // Determine actual status from the resulting message content type
                    String ct = message.getContentType().toLowerCase();
                    boolean outEncrypted = ct.contains("application/pkcs7-mime") && ct.contains("enveloped-data");
                    boolean outSigned = ct.contains("multipart/signed") || ct.contains("signed-data");
                    if (outEncrypted) {
                        // prepareOutgoing always signs before encrypting, so both are applied
                        emailMessage.setSigned(true);
                        emailMessage.setEncrypted(true);
                        emailMessage.setSmimeStatus(SmimeStatus.SIGNED_AND_ENCRYPTED);
                    } else if (outSigned) {
                        emailMessage.setSigned(true);
                        emailMessage.setSmimeStatus(SmimeStatus.SIGNED);
                    }
                } catch (Exception e) {
                    log.warn("S/MIME processing failed for outgoing email, sending unsigned: {}", e.getMessage());
                    emailMessage.setSmimeErrors(e.getMessage());
                }
            }

            // Send the email
            mailSender.send(message);
            
            // Save a copy to IMAP Sent folder
            saveToImapSentFolder(message);
            
            emailMessage.setStatus(EmailStatus.SENT);
            emailMessage.setSentDate(LocalDateTime.now());
            
            // Save the sent email to database
            EmailMessage savedEmail = saveSentEmail(emailMessage);
            
            log.info("Email sent successfully to: {}", String.join(", ", emailMessage.getTo()));
            
            return CompletableFuture.completedFuture(savedEmail);
        } catch (Exception e) {
            log.error("Failed to send email", e);
            emailMessage.setStatus(EmailStatus.FAILED);
            emailMessage.setErrorMessage(e.getMessage());
            
            // Save failed email to database (optional - can be useful for retry)
            EmailMessage savedEmail = saveFailedEmail(emailMessage);
            
            return CompletableFuture.completedFuture(savedEmail);
        }
    }
    
    public EmailMessage sendEmailWithTemplate(String templateName, Map<String, Object> variables, String to) {
        try {
            String htmlContent = templateService.processTemplate(templateName, variables);
            
            EmailMessage emailMessage = EmailMessage.builder()
                .messageId(generateMessageId())
                .from(properties.getGeneral().getFromAddress())
                .subject((String) variables.getOrDefault("subject", "Email Notification"))
                .htmlContent(htmlContent)
                .status(EmailStatus.PENDING)
                .build();
            
            emailMessage.getTo().add(to);
            
            return sendEmail(emailMessage).join();
        } catch (Exception e) {
            log.error("Failed to send template email", e);
            throw new RuntimeException("Failed to send template email", e);
        }
    }
    
    public EmailMessage sendEmailWithAttachments(EmailMessage emailMessage) {
        return sendEmail(emailMessage).join();
    }
    
    private MimeMessage createMimeMessage(EmailMessage emailMessage) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        // Set from
        helper.setFrom(emailMessage.getFrom() != null ? 
            emailMessage.getFrom() : properties.getGeneral().getFromAddress());
        
        // Set recipients
        helper.setTo(emailMessage.getTo().toArray(new String[0]));
        
        if (!emailMessage.getCc().isEmpty()) {
            helper.setCc(emailMessage.getCc().toArray(new String[0]));
        }
        
        if (!emailMessage.getBcc().isEmpty()) {
            helper.setBcc(emailMessage.getBcc().toArray(new String[0]));
        }
        
        // Set subject
        helper.setSubject(emailMessage.getSubject());
        
        // Set content
        boolean hasHtml = emailMessage.getHtmlContent() != null && !emailMessage.getHtmlContent().isEmpty();
        boolean hasText = emailMessage.getTextContent() != null && !emailMessage.getTextContent().isEmpty();
        
        if (hasHtml && hasText) {
            helper.setText(emailMessage.getTextContent(), emailMessage.getHtmlContent());
        } else if (hasHtml) {
            helper.setText(emailMessage.getHtmlContent(), true);
        } else if (hasText) {
            helper.setText(emailMessage.getTextContent(), false);
        }
        
        // Add attachments
        for (EmailAttachment attachment : emailMessage.getAttachments()) {
            String contentType = attachment.getContentType();
            
            // Use default content type if not provided or invalid
            if (contentType == null || contentType.trim().isEmpty()) {
                contentType = "application/octet-stream"; // Generic binary type
            }
            
            helper.addAttachment(
                attachment.getFilename(),
                new ByteArrayResource(attachment.getContent()),
                contentType
            );
        }
        
        return message;
    }
    
    private String generateMessageId() {
        return UUID.randomUUID().toString() + "@" + properties.getSmtp().getHost();
    }
    
    private EmailMessage saveSentEmail(EmailMessage emailMessage) {
        try {
            // Find or create the SENT folder
            EmailFolder sentFolder = folderRepository.findByType(FolderType.SENT)
                .orElseGet(() -> {
                    EmailFolder folder = EmailFolder.builder()
                        .name("SENT")
                        .displayName("Sent")
                        .icon("📤")
                        .type(FolderType.SENT)
                        .displayOrder(2)
                        .build();
                    return folderRepository.save(folder);
                });
            
            // Clear any existing folders and add to sent folder
            emailMessage.getFolders().clear();
            emailMessage.getFolders().add(sentFolder);
            
            // Save the email to database
            return messageRepository.save(emailMessage);
        } catch (Exception e) {
            log.error("Failed to save sent email to database", e);
            return emailMessage;
        }
    }
    
    private EmailMessage saveFailedEmail(EmailMessage emailMessage) {
        try {
            // Failed emails go to DRAFTS folder (or you could create a FAILED folder)
            EmailFolder draftsFolder = folderRepository.findByType(FolderType.DRAFTS)
                .orElseGet(() -> {
                    EmailFolder folder = EmailFolder.builder()
                        .name("DRAFTS")
                        .displayName("Drafts")
                        .icon("📝")
                        .type(FolderType.DRAFTS)
                        .displayOrder(3)
                        .build();
                    return folderRepository.save(folder);
                });
            
            // Clear any existing folders and add to drafts folder
            emailMessage.getFolders().clear();
            emailMessage.getFolders().add(draftsFolder);
            
            // Save the email to database
            return messageRepository.save(emailMessage);
        } catch (Exception e) {
            log.error("Failed to save failed email to database", e);
            return emailMessage;
        }
    }
    
    private void saveToImapSentFolder(MimeMessage message) {
        Folder sentFolder = null;
        try {
            // Get the sent folder name from configuration
            String sentFolderName = properties.getImap().getSentFolder();
            
            // Check if store is connected
            if (!imapStore.isConnected()) {
                log.warn("IMAP store is not connected. Attempting to reconnect...");
                imapStore.connect(
                    properties.getImap().getHost(),
                    properties.getImap().getUsername(),
                    properties.getImap().getPassword()
                );
            }
            
            // Get the Sent folder
            sentFolder = imapStore.getFolder(sentFolderName);
            
            // Create the folder if it doesn't exist
            if (!sentFolder.exists()) {
                log.info("Creating IMAP Sent folder: {}", sentFolderName);
                sentFolder.create(Folder.HOLDS_MESSAGES);
            }
            
            // Open the folder in read-write mode
            if (!sentFolder.isOpen()) {
                sentFolder.open(Folder.READ_WRITE);
            }
            
            // Convert MimeMessage to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            message.writeTo(baos);
            byte[] messageBytes = baos.toByteArray();
            
            // Create a new message from the byte array
            Session session = Session.getInstance(System.getProperties());
            MimeMessage messageToAppend = new MimeMessage(session, new java.io.ByteArrayInputStream(messageBytes));
            
            // Set the SEEN flag so it appears as read
            messageToAppend.setFlag(Flags.Flag.SEEN, true);
            
            // Append the message to the Sent folder
            sentFolder.appendMessages(new Message[]{messageToAppend});
            
            log.info("Email saved to IMAP Sent folder successfully");
            
        } catch (Exception e) {
            log.error("Failed to save email to IMAP Sent folder. Email was sent but not saved to server.", e);
            // Don't throw exception - email was sent successfully, just not saved to IMAP
        } finally {
            // Close the folder if it's open
            if (sentFolder != null && sentFolder.isOpen()) {
                try {
                    sentFolder.close(false);
                } catch (Exception e) {
                    log.error("Error closing IMAP Sent folder", e);
                }
            }
        }
    }
}