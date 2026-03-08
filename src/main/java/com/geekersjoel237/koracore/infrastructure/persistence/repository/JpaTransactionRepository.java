package com.geekersjoel237.koracore.infrastructure.persistence.repository;

import com.geekersjoel237.koracore.infrastructure.persistence.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaTransactionRepository extends JpaRepository<TransactionEntity, String> {
    List<TransactionEntity> findByFromIdOrToId(String fromId, String toId);
}