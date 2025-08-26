package com.igsl.group.email_service_sample.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTemplateService {
    private final TemplateEngine templateEngine;
    
    public String processTemplate(String templateName, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        
        try {
            return templateEngine.process(templateName, context);
        } catch (Exception e) {
            log.error("Error processing template: {}", templateName, e);
            throw new RuntimeException("Failed to process email template", e);
        }
    }
    
    public boolean validateTemplate(String templateName) {
        try {
            // Try to process with empty context to validate template exists
            templateEngine.process(templateName, new Context());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}