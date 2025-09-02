package com.igsl.group.email_service_sample.exception;

public class EmailNotFoundException extends RuntimeException {
    public EmailNotFoundException(Long id) {
        super("Email not found with ID: " + id);
    }
}