package com.igsl.group.email_service_sample.dto;

import com.igsl.group.email_service_sample.model.EmailMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailResponse {
    private String messageId;
    private String status;
    private String errorMessage;
    
    public EmailResponse(EmailMessage emailMessage) {
        this.messageId = emailMessage.getMessageId();
        this.status = emailMessage.getStatus().toString();
        this.errorMessage = emailMessage.getErrorMessage();
    }
}