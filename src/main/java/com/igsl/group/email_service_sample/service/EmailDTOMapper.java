package com.igsl.group.email_service_sample.service;

import com.igsl.group.email_service_sample.dto.EmailAttachmentDTO;
import com.igsl.group.email_service_sample.dto.EmailFolderDTO;
import com.igsl.group.email_service_sample.dto.EmailMessageDTO;
import com.igsl.group.email_service_sample.dto.EmailMessageSummaryDTO;
import com.igsl.group.email_service_sample.model.EmailAttachment;
import com.igsl.group.email_service_sample.model.EmailFolder;
import com.igsl.group.email_service_sample.model.EmailMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmailDTOMapper {
    
    public EmailMessageDTO toEmailMessageDTO(EmailMessage entity) {
        if (entity == null) {
            return null;
        }
        
        return EmailMessageDTO.builder()
                .id(entity.getId())
                .messageId(entity.getMessageId())
                .from(entity.getFrom())
                .to(entity.getTo())
                .cc(entity.getCc())
                .bcc(entity.getBcc())
                .subject(entity.getSubject())
                .textContent(entity.getTextContent())
                .htmlContent(entity.getHtmlContent())
                .sentDate(entity.getSentDate())
                .receivedDate(entity.getReceivedDate())
                .status(entity.getStatus() != null ? entity.getStatus().toString() : null)
                .errorMessage(entity.getErrorMessage())
                .read(entity.isRead())
                .flagged(entity.isFlagged())
                .important(entity.isImportant())
                .spam(entity.isSpam())
                .deleted(entity.isDeleted())
                .readDate(entity.getReadDate())
                .flaggedDate(entity.getFlaggedDate())
                .attachments(entity.getAttachments().stream()
                        .map(this::toEmailAttachmentDTO)
                        .collect(Collectors.toList()))
                .folders(entity.getFolders().stream()
                        .map(this::toEmailFolderDTO)
                        .collect(Collectors.toSet()))
                .labels(entity.getLabels())
                .imapUid(entity.getImapUid())
                .imapFolder(entity.getImapFolder())
                .signed(entity.isSigned())
                .encrypted(entity.isEncrypted())
                .signatureValid(entity.isSignatureValid())
                .signerEmail(entity.getSignerEmail())
                .signatureTimestamp(entity.getSignatureTimestamp())
                .build();
    }
    
    public EmailMessageSummaryDTO toEmailMessageSummaryDTO(EmailMessage entity) {
        if (entity == null) {
            return null;
        }
        
        String preview = "";
        if (entity.getTextContent() != null && !entity.getTextContent().isEmpty()) {
            preview = entity.getTextContent().length() > 100 
                    ? entity.getTextContent().substring(0, 100) + "..." 
                    : entity.getTextContent();
        }
        
        return EmailMessageSummaryDTO.builder()
                .id(entity.getId())
                .messageId(entity.getMessageId())
                .from(entity.getFrom())
                .subject(entity.getSubject())
                .receivedDate(entity.getReceivedDate())
                .read(entity.isRead())
                .flagged(entity.isFlagged())
                .important(entity.isImportant())
                .hasAttachments(!entity.getAttachments().isEmpty())
                .labels(entity.getLabels())
                .preview(preview)
                .build();
    }
    
    public EmailAttachmentDTO toEmailAttachmentDTO(EmailAttachment entity) {
        if (entity == null) {
            return null;
        }
        
        return EmailAttachmentDTO.builder()
                .id(entity.getId())
                .filename(entity.getFilename())
                .contentType(entity.getContentType())
                .size(entity.getSize())
                .build();
    }
    
    public EmailFolderDTO toEmailFolderDTO(EmailFolder entity) {
        if (entity == null) {
            return null;
        }
        
        return EmailFolderDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .displayName(entity.getDisplayName())
                .icon(entity.getIcon())
                .type(entity.getType() != null ? entity.getType().toString() : null)
                .displayOrder(entity.getDisplayOrder())
                .emailCount(0L) // Count will be set separately if needed
                .build();
    }
    
    public Page<EmailMessageSummaryDTO> toEmailMessageSummaryDTOPage(Page<EmailMessage> entityPage) {
        List<EmailMessageSummaryDTO> dtoList = entityPage.getContent().stream()
                .map(this::toEmailMessageSummaryDTO)
                .collect(Collectors.toList());
        
        return new PageImpl<>(dtoList, entityPage.getPageable(), entityPage.getTotalElements());
    }
    
    public Page<EmailMessageDTO> toEmailMessageDTOPage(Page<EmailMessage> entityPage) {
        List<EmailMessageDTO> dtoList = entityPage.getContent().stream()
                .map(this::toEmailMessageDTO)
                .collect(Collectors.toList());
        
        return new PageImpl<>(dtoList, entityPage.getPageable(), entityPage.getTotalElements());
    }
}