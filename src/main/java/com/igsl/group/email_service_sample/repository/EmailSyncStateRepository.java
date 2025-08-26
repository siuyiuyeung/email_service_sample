package com.igsl.group.email_service_sample.repository;

import com.igsl.group.email_service_sample.model.EmailSyncState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailSyncStateRepository extends JpaRepository<EmailSyncState, String> {
    Optional<EmailSyncState> findByFolderName(String folderName);
}