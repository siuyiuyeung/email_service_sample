package com.igsl.group.email_service_sample.repository;

import com.igsl.group.email_service_sample.model.EmailFolder;
import com.igsl.group.email_service_sample.model.FolderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailFolderRepository extends JpaRepository<EmailFolder, Long> {
    Optional<EmailFolder> findByName(String name);
    
    List<EmailFolder> findAllByOrderByDisplayOrderAsc();
    
    Optional<EmailFolder> findByType(FolderType type);
}