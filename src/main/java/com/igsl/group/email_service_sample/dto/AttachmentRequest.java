package com.igsl.group.email_service_sample.dto;

import lombok.Data;

@Data
public class AttachmentRequest {
    private String filename;
    private String contentType;
    private byte[] content;
}