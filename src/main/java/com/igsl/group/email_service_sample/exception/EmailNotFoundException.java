package com.igsl.group.email_service_sample.exception;

public class EmailNotFoundException extends RuntimeException {
    public EmailNotFoundException(String messageId) {
        super("Email not found with ID: " + messageId);
    }
}