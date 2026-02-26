package com.igsl.group.email_service_sample.dto;

import com.igsl.group.email_service_sample.model.Certificate;
import com.igsl.group.email_service_sample.model.CertificateStatus;
import com.igsl.group.email_service_sample.model.CertificateType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class CertificateDTO {
    private Long id;
    private String emailAddress;
    private String serialNumber;
    private String subjectDN;
    private String issuerDN;
    private LocalDateTime notBefore;
    private LocalDateTime notAfter;
    private String thumbprint;
    private CertificateType type;
    private CertificateStatus status;
    private boolean trusted;
    private LocalDateTime importedAt;
    private LocalDateTime lastUsedAt;
    private Set<String> keyUsages;
    private Set<String> extendedKeyUsages;

    public static CertificateDTO from(Certificate cert) {
        CertificateDTO dto = new CertificateDTO();
        dto.setId(cert.getId());
        dto.setEmailAddress(cert.getEmailAddress());
        dto.setSerialNumber(cert.getSerialNumber());
        dto.setSubjectDN(cert.getSubjectDN());
        dto.setIssuerDN(cert.getIssuerDN());
        dto.setNotBefore(cert.getNotBefore());
        dto.setNotAfter(cert.getNotAfter());
        dto.setThumbprint(cert.getThumbprint());
        dto.setType(cert.getType());
        dto.setStatus(cert.getStatus());
        dto.setTrusted(cert.isTrusted());
        dto.setImportedAt(cert.getImportedAt());
        dto.setLastUsedAt(cert.getLastUsedAt());
        dto.setKeyUsages(cert.getKeyUsages());
        dto.setExtendedKeyUsages(cert.getExtendedKeyUsages());
        return dto;
    }
}
