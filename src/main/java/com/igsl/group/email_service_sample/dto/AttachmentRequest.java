package com.igsl.group.email_service_sample.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;
import java.util.Base64;

@Data
public class AttachmentRequest {
    private String filename;
    private String contentType;
    private byte[] content;
    
    // Custom setter to handle base64 string
    @JsonSetter("content")
    public void setContentFromBase64(String base64) {
        if (base64 != null) {
            this.content = Base64.getDecoder().decode(base64);
        }
    }
}