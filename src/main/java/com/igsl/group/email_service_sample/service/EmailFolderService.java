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

import javax.annotation.PostConstruct;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
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
        if (!folderRepository.findByName(name).isPresent()) {
            EmailFolder folder = EmailFolder.builder()
                .name(name)
                .displayName(displayName)
                .icon(icon)
                .type(type)
                .displayOrder(order)
                .build();
            folderRepository.save(folder);
            log.info("Created default folder: {}", name);
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
    
    public List<EmailFolder> getAllFolders() {
        return folderRepository.findAllByOrderByDisplayOrderAsc();
    }
}