package com.igsl.group.email_service_sample.repository;

import com.igsl.group.email_service_sample.model.SyncLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface SyncLockRepository extends JpaRepository<SyncLock, String> {
    @Modifying
    @Query("DELETE FROM SyncLock s WHERE s.expiresAt < :now")
    int deleteExpiredLocks(@Param("now") LocalDateTime now);
}