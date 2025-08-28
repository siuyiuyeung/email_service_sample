package com.igsl.group.email_service_sample.repository;

import com.igsl.group.email_service_sample.model.EmailFolder;
import com.igsl.group.email_service_sample.model.EmailMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {
    Page<EmailMessage> findByIsDeletedFalseOrderByReceivedDateDesc(Pageable pageable);
    
    Page<EmailMessage> findByFoldersContainingAndIsDeletedFalse(EmailFolder folder, Pageable pageable);
    
    Page<EmailMessage> findByIsReadFalseAndIsDeletedFalse(Pageable pageable);
    
    Page<EmailMessage> findByIsFlaggedTrueAndIsDeletedFalse(Pageable pageable);
    
    Page<EmailMessage> findByIsImportantTrueAndIsDeletedFalse(Pageable pageable);
    
    @Query("SELECT e FROM EmailMessage e WHERE e.isDeleted = false AND " +
           "(LOWER(e.subject) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(e.from) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(e.textContent) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<EmailMessage> searchEmails(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    Optional<EmailMessage> findByImapUidAndImapFolder(Long imapUid, String imapFolder);
    
    Optional<EmailMessage> findByMessageId(String messageId);
    
    long countByIsReadFalseAndIsDeletedFalse();
}