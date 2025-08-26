package com.igsl.group.email_service_sample.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailRequest {
    @NotBlank
    @Email
    private String to;
    
    @Email
    private String cc;
    
    @Email
    private String bcc;
    
    @NotBlank
    private String subject;
    
    private String textContent;
    private String htmlContent;
    
    private List<AttachmentRequest> attachments;
}