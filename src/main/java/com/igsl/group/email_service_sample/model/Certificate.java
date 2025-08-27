package com.igsl.group.email_service_sample.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "certificates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class        Certificate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String emailAddress;
    
    @Column(unique = true, nullable = false)
    private String serialNumber;
    
    @Lob
    @Column(nullable = false)
    private byte[] certificateData;
    
    private String subjectDN;
    private String issuerDN;
    private LocalDateTime notBefore;
    private LocalDateTime notAfter;
    private String thumbprint;
    
    @Enumerated(EnumType.STRING)
    private CertificateType type;
    
    @Enumerated(EnumType.STRING)
    private CertificateStatus status;
    
    private boolean trusted = false;
    private LocalDateTime importedAt;
    private LocalDateTime lastUsedAt;
    
    @ElementCollection
    private Set<String> keyUsages = new HashSet<>();
    
    @ElementCollection
    private Set<String> extendedKeyUsages = new HashSet<>();
}