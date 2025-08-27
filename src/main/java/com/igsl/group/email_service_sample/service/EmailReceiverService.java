package com.igsl.group.email_service_sample.service;

import com.igsl.group.email_service_sample.config.EmailProperties;
import com.igsl.group.email_service_sample.event.EmailEventPublisher;
import com.igsl.group.email_service_sample.event.NewEmailEvent;
import com.igsl.group.email_service_sample.model.*;
import com.igsl.group.email_service_sample.repository.EmailFolderRepository;
import com.igsl.group.email_service_sample.repository.EmailMessageRepository;
import com.igsl.group.email_service_sample.repository.EmailSyncStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.ReceivedDateTerm;
import javax.mail.search.SearchTerm;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailReceiverService {
    private final Store imapStore;
    private final EmailProperties properties;
    private final EmailMessageRepository messageRepository;
    private final EmailFolderRepository folderRepository;
    private final EmailSyncStateRepository syncStateRepository;
    private final EmailEventPublisher eventPublisher;
    private final DistributedLockService lockService;
    
    @Scheduled(fixedDelayString = "${email.imap.poll-interval:60000}")
    public void pollEmails() {
        String lockKey = "imap_sync_" + properties.getImap().getFolder();
        
        if (!lockService.acquireLock(lockKey, Duration.ofMinutes(1))) {
            log.info("Another instance is already syncing folder: {}", 
                    properties.getImap().getFolder());
            return;
        }
        
        try {
            performEmailSync();
        } finally {
            lockService.releaseLock(lockKey);
        }
    }
    
    @Transactional
    public void performEmailSync() {
        try {
            Folder folder = imapStore.getFolder(properties.getImap().getFolder());
            folder.open(Folder.READ_WRITE);
            
            // Get last sync state
            EmailSyncState syncState = syncStateRepository
                .findByFolderName(folder.getName())
                .orElse(new EmailSyncState(folder.getName()));
            
            // Search for new messages
            SearchTerm searchTerm = new ReceivedDateTerm(ComparisonTerm.GT, syncState.getLastSyncDate());
            Message[] messages = folder.search(searchTerm);
            
            log.info("Found {} new messages in folder {}", messages.length, folder.getName());
            
            for (Message message : messages) {
                processMessage(message, folder.getName());
            }
            
            // Update sync state
            syncState.setLastSyncDate(new Date());
            if (folder instanceof UIDFolder) {
                syncState.setLastUidValidity(((UIDFolder) folder).getUIDValidity());
            }
            syncStateRepository.save(syncState);
            
            folder.close(false);
        } catch (Exception e) {
            log.error("Error during email sync", e);
        }
    }
    
    @Transactional
    public void processMessage(Message message, String folderName) {
        try {
            if (!(message instanceof MimeMessage)) {
                return;
            }
            
            MimeMessage mimeMessage = (MimeMessage) message;
            long uid = -1;
            
            if (message.getFolder() instanceof UIDFolder) {
                uid = ((UIDFolder) message.getFolder()).getUID(message);
                
                // Check if email already exists (idempotency)
                if (messageRepository.findByImapUidAndImapFolder(uid, folderName).isPresent()) {
                    log.debug("Email already synced: UID={}, Folder={}", uid, folderName);
                    return;
                }
            }
            
            // Convert and save email
            EmailMessage emailMessage = convertToEmailMessage(mimeMessage, uid, folderName);
            
            // Assign to inbox folder
            EmailFolder inboxFolder = folderRepository.findByType(FolderType.INBOX)
                .orElseThrow(() -> new RuntimeException("INBOX folder not found"));
            emailMessage.getFolders().add(inboxFolder);
            
            messageRepository.save(emailMessage);
            
            // Publish event
            eventPublisher.publishNewEmailEvent(emailMessage);
            
            log.info("Processed new email: {} from {}", emailMessage.getSubject(), emailMessage.getFrom());
            
        } catch (Exception e) {
            log.error("Error processing message", e);
        }
    }
    
    private EmailMessage convertToEmailMessage(MimeMessage message, long uid, String folderName) throws Exception {
        EmailMessage emailMessage = EmailMessage.builder()
            .messageId(message.getMessageID() != null ? message.getMessageID() : UUID.randomUUID().toString())
            .subject(message.getSubject())
            .imapUid(uid > 0 ? uid : null)
            .imapFolder(folderName)
            .status(EmailStatus.RECEIVED)
            .receivedDate(LocalDateTime.now())
            .build();
        
        // Set from
        Address[] fromAddresses = message.getFrom();
        if (fromAddresses != null && fromAddresses.length > 0) {
            emailMessage.setFrom(((InternetAddress) fromAddresses[0]).getAddress());
        }
        
        // Set recipients
        emailMessage.setTo(extractEmailAddresses(message.getRecipients(Message.RecipientType.TO)));
        emailMessage.setCc(extractEmailAddresses(message.getRecipients(Message.RecipientType.CC)));
        emailMessage.setBcc(extractEmailAddresses(message.getRecipients(Message.RecipientType.BCC)));
        
        // Set dates
        if (message.getSentDate() != null) {
            emailMessage.setSentDate(
                LocalDateTime.ofInstant(message.getSentDate().toInstant(), ZoneId.systemDefault())
            );
        }
        
        // Process content
        processMessageContent(message, emailMessage);
        
        // Set flags
        emailMessage.setRead(message.isSet(Flags.Flag.SEEN));
        emailMessage.setFlagged(message.isSet(Flags.Flag.FLAGGED));
        emailMessage.setDeleted(message.isSet(Flags.Flag.DELETED));
        
        // Extract important headers only
        extractImportantHeaders(message, emailMessage);
        
        return emailMessage;
    }
    
    private void processMessageContent(Part part, EmailMessage emailMessage) throws Exception {
        if (part.isMimeType("text/plain")) {
            emailMessage.setTextContent((String) part.getContent());
        } else if (part.isMimeType("text/html")) {
            emailMessage.setHtmlContent((String) part.getContent());
        } else if (part.isMimeType("multipart/*")) {
            MimeMultipart multipart = (MimeMultipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                processMessageContent(multipart.getBodyPart(i), emailMessage);
            }
        } else if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || 
                   Part.INLINE.equalsIgnoreCase(part.getDisposition())) {
            // Process attachment
            EmailAttachment attachment = EmailAttachment.builder()
                .filename(part.getFileName())
                .contentType(part.getContentType())
                .content(readAttachmentContent(part))
                .size(part.getSize())
                .build();
            emailMessage.getAttachments().add(attachment);
        }
    }
    
    private byte[] readAttachmentContent(Part part) throws Exception {
        try (InputStream inputStream = part.getInputStream()) {
            // Java 8 compatible way to read all bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        }
    }
    
    private List<String> extractEmailAddresses(Address[] addresses) {
        if (addresses == null) {
            return new ArrayList<>();
        }
        
        return Arrays.stream(addresses)
            .map(addr -> ((InternetAddress) addr).getAddress())
            .collect(Collectors.toList());
    }
    
    private void extractImportantHeaders(MimeMessage message, EmailMessage emailMessage) throws Exception {
        // Define important headers to keep
        Set<String> importantHeaders = new HashSet<>(Arrays.asList(
            "Message-ID", "In-Reply-To", "References", "Reply-To",
            "Return-Path", "X-Priority", "X-Mailer", "Content-Type",
            "MIME-Version", "X-Spam-Score", "X-Spam-Flag"
        ));
        
        Enumeration<Header> headers = message.getAllHeaders();
        while (headers.hasMoreElements()) {
            Header header = headers.nextElement();
            String headerName = header.getName();
            String headerValue = header.getValue();
            
            // Only store important headers or those that aren't too long
            if (importantHeaders.contains(headerName) || 
                (headerValue != null && headerValue.length() <= 500)) {
                
                // Truncate very long header values
                if (headerValue != null && headerValue.length() > 1000) {
                    headerValue = headerValue.substring(0, 997) + "...";
                }
                
                emailMessage.getHeaders().put(headerName, headerValue);
            }
        }
    }
    
    public List<EmailMessage> fetchEmails(int maxMessages) {
        // Implementation for fetching emails with pagination
        return messageRepository.findAll()
            .stream()
            .limit(maxMessages)
            .collect(Collectors.toList());
    }
    
    public EmailMessage fetchEmailById(String messageId) {
        return messageRepository.findById(messageId).orElse(null);
    }
    
    @EventListener
    public void handleNewEmail(NewEmailEvent event) {
        log.info("New email event received: {}", event.getEmailMessage().getSubject());
        // Additional processing for new emails can be added here
    }
}