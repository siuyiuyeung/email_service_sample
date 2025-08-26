package com.igsl.group.email_service_sample.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateFolderRequest {
    @NotBlank
    private String name;
    
    @NotBlank
    private String displayName;
    
    private String icon = "📁";
}