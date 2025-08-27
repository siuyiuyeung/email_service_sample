package com.igsl.group.email_service_sample.model;

import lombok.*;

import javax.persistence.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "email_attachments")
@EqualsAndHashCode(exclude = "content")
@ToString(exclude = "content")
public class EmailAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String filename;
    private String contentType;
    
    @Lob
    private byte[] content;
    
    private long size;
}