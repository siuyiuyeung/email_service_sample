package com.igsl.group.email_service_sample.repository;

import com.igsl.group.email_service_sample.model.Certificate;
import com.igsl.group.email_service_sample.model.CertificateStatus;
import com.igsl.group.email_service_sample.model.CertificateType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    Optional<Certificate> findByEmailAddressAndType(String emailAddress, CertificateType type);
    
    List<Certificate> findByEmailAddress(String emailAddress);
    
    List<Certificate> findByStatusAndNotAfterBefore(CertificateStatus status, LocalDateTime date);
    
    Optional<Certificate> findBySerialNumber(String serialNumber);
    
    Optional<Certificate> findByThumbprint(String thumbprint);
}