package com.igsl.group.email_service_sample.service;

import com.igsl.group.email_service_sample.exception.EmailNotFoundException;
import com.igsl.group.email_service_sample.exception.FolderNotFoundException;
import com.igsl.group.email_service_sample.model.EmailFolder;
import com.igsl.group.email_service_sample.model.EmailMessage;
import com.igsl.group.email_service_sample.model.FolderType;
import com.igsl.group.email_service_sample.repository.EmailFolderRepository;
import com.igsl.group.email_service_sample.repository.EmailMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.mail.*;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class EmailMarkingService {
    private final EmailMessageRepository messageRepository;
    private final EmailFolderRepository folderRepository;
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
        if (email.getImapFolder() == null || email.getImapUid() == null) {
            return; // Not an IMAP message
        }
        
        // Update flag on IMAP server
        try {
            Folder folder = imapStore.getFolder(email.getImapFolder());
            if (!folder.isOpen()) {
                folder.open(Folder.READ_WRITE);
            }
            
            UIDFolder uidFolder = (UIDFolder) folder;
            Message message = uidFolder.getMessageByUID(email.getImapUid());
            if (message != null) {
                message.setFlag(flag, set);
            }
            
            if (folder.isOpen()) {
                folder.close(false);
            }
        } catch (Exception e) {
            log.error("Failed to update IMAP flag for message {}: {}", email.getMessageId(), e.getMessage());
        }
    }
}