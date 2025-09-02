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
@EqualsAndHashCode(exclude = {"content", "emailMessage"})
@ToString(exclude = {"content", "emailMessage"})
public class EmailAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String filename;
    @Column(columnDefinition = "VARCHAR(1000)")
    private String contentType;
    
    @Lob
    private byte[] content;
    
    private long size;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_message_id")
    private EmailMessage emailMessage;
}