package com.geekersjoel237.koracore.infrastructure.persistence.repository;

import com.geekersjoel237.koracore.infrastructure.persistence.entities.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataTransactionRepository
        extends JpaRepository<TransactionEntity, String>,
                JpaSpecificationExecutor<TransactionEntity> {

    List<TransactionEntity> findByFromAccountIdOrToAccountId(String fromAccountId, String toAccountId);
}