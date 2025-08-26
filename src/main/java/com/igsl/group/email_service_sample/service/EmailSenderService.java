package com.igsl.group.email_service_sample.service;

import com.igsl.group.email_service_sample.config.EmailProperties;
import com.igsl.group.email_service_sample.model.EmailAttachment;
import com.igsl.group.email_service_sample.model.EmailMessage;
import com.igsl.group.email_service_sample.model.EmailStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.mail.internet.MimeMessage;
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
    
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailMessage> sendEmail(EmailMessage emailMessage) {
        try {
            MimeMessage message = createMimeMessage(emailMessage);
            mailSender.send(message);
            
            emailMessage.setStatus(EmailStatus.SENT);
            emailMessage.setSentDate(LocalDateTime.now());
            log.info("Email sent successfully to: {}", String.join(", ", emailMessage.getTo()));
            
            return CompletableFuture.completedFuture(emailMessage);
        } catch (Exception e) {
            log.error("Failed to send email", e);
            emailMessage.setStatus(EmailStatus.FAILED);
            emailMessage.setErrorMessage(e.getMessage());
            return CompletableFuture.completedFuture(emailMessage);
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
            helper.addAttachment(
                attachment.getFilename(),
                new ByteArrayResource(attachment.getContent()),
                attachment.getContentType()
            );
        }
        
        return message;
    }
    
    private String generateMessageId() {
        return UUID.randomUUID().toString() + "@" + properties.getSmtp().getHost();
    }
}