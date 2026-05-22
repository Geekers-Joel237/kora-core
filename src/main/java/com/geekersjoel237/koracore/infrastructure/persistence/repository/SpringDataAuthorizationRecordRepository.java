package com.geekersjoel237.koracore.infrastructure.persistence.repository;

import com.geekersjoel237.koracore.infrastructure.persistence.entities.AuthorizationRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataAuthorizationRecordRepository
        extends JpaRepository<AuthorizationRecordEntity, String> {

    Optional<AuthorizationRecordEntity>
        findFirstByTransactionIdAndStatusAndDeletedAtIsNull(
            String transactionId, String status);

    @Query("SELECT a FROM AuthorizationRecordEntity a " +
           "WHERE a.status = 'ACTIVE' " +
           "AND a.expiresAt < :now " +
           "AND a.deletedAt IS NULL")
    List<AuthorizationRecordEntity> findExpiredActive(@Param("now") Instant now);
}