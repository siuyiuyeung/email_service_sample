package com.igsl.group.email_service_sample.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "email_folders")
public class EmailFolder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private String displayName;
    private String icon;
    
    @Enumerated(EnumType.STRING)
    private FolderType type;
    
    private int displayOrder;
    
    @ManyToMany(mappedBy = "folders")
    private Set<EmailMessage> emails = new HashSet<>();
}