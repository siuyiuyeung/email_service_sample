package com.igsl.group.email_service_sample.controller;

import com.igsl.group.email_service_sample.dto.*;
import com.igsl.group.email_service_sample.model.EmailAttachment;
import com.igsl.group.email_service_sample.model.EmailFolder;
import com.igsl.group.email_service_sample.model.EmailMessage;
import com.igsl.group.email_service_sample.model.EmailStatus;
import com.igsl.group.email_service_sample.repository.EmailFolderRepository;
import com.igsl.group.email_service_sample.repository.EmailMessageRepository;
import com.igsl.group.email_service_sample.service.EmailDTOMapper;
import com.igsl.group.email_service_sample.service.EmailFolderService;
import com.igsl.group.email_service_sample.service.EmailMarkingService;
import com.igsl.group.email_service_sample.service.EmailReceiverService;
import com.igsl.group.email_service_sample.service.EmailSenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final EmailFolderRepository folderRepository;
    private final EmailDTOMapper dtoMapper;
    
    // Sending endpoints
    @PostMapping("/send")
    public ResponseEntity<EmailResponse> sendEmail(@Valid @RequestBody EmailRequest request) {
        EmailMessage emailMessage = buildEmailMessage(request);
        EmailMessage sentEmail = senderService.sendEmail(emailMessage).join();
        return ResponseEntity.ok(new EmailResponse(sentEmail));
    }
    
    @PostMapping("/send-template")
    public ResponseEntity<EmailResponse> sendTemplateEmail(@Valid @RequestBody TemplateEmailRequest request) {
        if (request.getTemplateVariables() != null && request.getSubject() != null) {
            request.getTemplateVariables().put("subject", request.getSubject());
        }
        EmailMessage sentEmail = senderService.sendEmailWithTemplate(
            request.getTemplateName(), 
            request.getTemplateVariables(), 
            request.getTo()
        );
        return ResponseEntity.ok(new EmailResponse(sentEmail));
    }
    
    // Inbox and folder endpoints
    @GetMapping("/inbox")
    public ResponseEntity<Page<EmailMessageSummaryDTO>> getInboxEmails(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String filter) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<EmailMessage> emails;
        
        if (filter == null || filter.isEmpty()) {
            emails = messageRepository.findByIsDeletedFalseOrderByReceivedDateDesc(pageable);
        } else {
            switch (filter.toLowerCase()) {
                case "unread":
                    emails = messageRepository.findByIsReadFalseAndIsDeletedFalse(pageable);
                    break;
                case "flagged":
                    emails = messageRepository.findByIsFlaggedTrueAndIsDeletedFalse(pageable);
                    break;
                case "important":
                    emails = messageRepository.findByIsImportantTrueAndIsDeletedFalse(pageable);
                    break;
                default:
                    emails = messageRepository.findByIsDeletedFalseOrderByReceivedDateDesc(pageable);
            }
        }
        
        return ResponseEntity.ok(dtoMapper.toEmailMessageSummaryDTOPage(emails));
    }
    
    @GetMapping("/folder/{folderId}")
    public ResponseEntity<Page<EmailMessageSummaryDTO>> getFolderEmails(
            @PathVariable Long folderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        EmailFolder folder = folderRepository.findById(folderId)
            .orElseThrow(() -> new RuntimeException("Folder not found"));
        
        Pageable pageable = PageRequest.of(page, size);
        Page<EmailMessage> emails = messageRepository.findByFoldersContainingAndIsDeletedFalse(folder, pageable);
        
        return ResponseEntity.ok(dtoMapper.toEmailMessageSummaryDTOPage(emails));
    }
    
    @GetMapping("/search")
    public ResponseEntity<Page<EmailMessageSummaryDTO>> searchEmails(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<EmailMessage> emails = messageRepository.searchEmails(query, pageable);
        return ResponseEntity.ok(dtoMapper.toEmailMessageSummaryDTOPage(emails));
    }
    
    @GetMapping("/{messageId}")
    public ResponseEntity<EmailMessageDTO> getEmail(@PathVariable String messageId) {
        EmailMessage email = receiverService.fetchEmailByMessageId(messageId);
        if (email != null && !email.isRead()) {
            markingService.markAsRead(messageId);
        }
        return email != null ? ResponseEntity.ok(dtoMapper.toEmailMessageDTO(email)) : ResponseEntity.notFound().build();
    }
    
    // Marking endpoints
    @PutMapping("/{messageId}/mark-read")
    public ResponseEntity<EmailMessageDTO> markAsRead(@PathVariable String messageId) {
        return ResponseEntity.ok(dtoMapper.toEmailMessageDTO(markingService.markAsRead(messageId)));
    }
    
    @PutMapping("/{messageId}/mark-unread")
    public ResponseEntity<EmailMessageDTO> markAsUnread(@PathVariable String messageId) {
        return ResponseEntity.ok(dtoMapper.toEmailMessageDTO(markingService.markAsUnread(messageId)));
    }
    
    @PutMapping("/{messageId}/toggle-flag")
    public ResponseEntity<EmailMessageDTO> toggleFlag(@PathVariable String messageId) {
        return ResponseEntity.ok(dtoMapper.toEmailMessageDTO(markingService.toggleFlag(messageId)));
    }
    
    @PutMapping("/{messageId}/mark-important")
    public ResponseEntity<EmailMessageDTO> markAsImportant(
            @PathVariable String messageId,
            @RequestParam boolean important) {
        return ResponseEntity.ok(dtoMapper.toEmailMessageDTO(markingService.markAsImportant(messageId, important)));
    }
    
    @PutMapping("/{messageId}/mark-spam")
    public ResponseEntity<EmailMessageDTO> markAsSpam(
            @PathVariable String messageId,
            @RequestParam boolean spam) {
        return ResponseEntity.ok(dtoMapper.toEmailMessageDTO(markingService.markAsSpam(messageId, spam)));
    }
    
    // Bulk operations
    @PutMapping("/bulk/mark-read")
    public ResponseEntity<Void> markMultipleAsRead(@RequestBody List<String> messageIds) {
        markingService.markMultipleAsRead(messageIds);
        return ResponseEntity.ok().build();
    }
    
    // Folder management
    @GetMapping("/folders")
    public ResponseEntity<List<EmailFolderDTO>> getFolders() {
        return ResponseEntity.ok(folderService.getAllFolders().stream()
                .map(dtoMapper::toEmailFolderDTO)
                .collect(Collectors.toList()));
    }
    
    @PostMapping("/folders")
    public ResponseEntity<EmailFolderDTO> createFolder(@Valid @RequestBody CreateFolderRequest request) {
        EmailFolder folder = folderService.createCustomFolder(
            request.getName(), request.getDisplayName(), request.getIcon());
        return ResponseEntity.ok(dtoMapper.toEmailFolderDTO(folder));
    }
    
    @PutMapping("/{messageId}/move-to-folder/{folderId}")
    public ResponseEntity<EmailMessageDTO> moveToFolder(
            @PathVariable String messageId,
            @PathVariable Long folderId) {
        return ResponseEntity.ok(dtoMapper.toEmailMessageDTO(folderService.moveToFolder(messageId, folderId)));
    }
    
    // Labels
    @PutMapping("/{messageId}/labels/{label}")
    public ResponseEntity<EmailMessageDTO> addLabel(
            @PathVariable String messageId,
            @PathVariable String label) {
        return ResponseEntity.ok(dtoMapper.toEmailMessageDTO(folderService.addLabel(messageId, label)));
    }
    
    @DeleteMapping("/{messageId}/labels/{label}")
    public ResponseEntity<EmailMessageDTO> removeLabel(
            @PathVariable String messageId,
            @PathVariable String label) {
        return ResponseEntity.ok(dtoMapper.toEmailMessageDTO(folderService.removeLabel(messageId, label)));
    }
    
    // Statistics
    @GetMapping("/stats/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount() {
        long count = messageRepository.countByIsReadFalseAndIsDeletedFalse();
        return ResponseEntity.ok(new UnreadCountResponse(count));
    }
    
    // Helper methods
    private EmailMessage buildEmailMessage(EmailRequest request) {
        EmailMessage email = EmailMessage.builder()
            .messageId(UUID.randomUUID().toString())
            .subject(request.getSubject())
            .textContent(request.getTextContent())
            .htmlContent(request.getHtmlContent())
            .status(EmailStatus.PENDING)
            .receivedDate(LocalDateTime.now())
            .build();
        
        email.getTo().add(request.getTo());
        
        if (request.getCc() != null) {
            email.getCc().add(request.getCc());
        }
        
        if (request.getBcc() != null) {
            email.getBcc().add(request.getBcc());
        }
        
        if (request.getAttachments() != null) {
            List<EmailAttachment> attachments = request.getAttachments().stream()
                .map(att -> EmailAttachment.builder()
                    .filename(att.getFilename())
                    .contentType(att.getContentType())
                    .content(att.getContent())
                    .size(att.getContent().length)
                    .build())
                .collect(Collectors.toList());
            email.setAttachments(attachments);
        }
        
        return email;
    }
}