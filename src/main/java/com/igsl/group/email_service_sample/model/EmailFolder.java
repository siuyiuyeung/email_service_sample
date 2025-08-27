package com.igsl.group.email_service_sample.model;

import lombok.*;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "email_folders")
@EqualsAndHashCode(exclude = "emails")
@ToString(exclude = "emails")
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
    @Builder.Default
    private Set<EmailMessage> emails = new HashSet<>();
}