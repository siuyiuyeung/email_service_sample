package com.igsl.group.email_service_sample.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import java.util.Map;

@Data
public class TemplateEmailRequest {
    @NotBlank
    @Email
    private String to;
    
    @NotBlank
    private String templateName;
    
    private Map<String, Object> templateVariables;
    
    private String subject;
}