package com.geekersjoel237.koracore.infrastructure.persistence.repository;

import com.geekersjoel237.koracore.infrastructure.persistence.entity.LedgerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaLedgerRepository extends JpaRepository<LedgerEntity, String> {
    Optional<LedgerEntity> findFirstBy();
}