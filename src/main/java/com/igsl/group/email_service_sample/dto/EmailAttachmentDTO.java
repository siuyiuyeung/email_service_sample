package com.igsl.group.email_service_sample.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailAttachmentDTO {
    private Long id;
    private String filename;
    private String contentType;
    private long size;
    // Note: content is not included in DTO for performance reasons
    // Add a separate endpoint to download attachment content by ID
}