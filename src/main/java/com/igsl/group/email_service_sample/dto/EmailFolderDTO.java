package com.igsl.group.email_service_sample.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailFolderDTO {
    private Long id;
    private String name;
    private String displayName;
    private String icon;
    private String type;
    private int displayOrder;
    private long emailCount; // Count of emails in this folder
}