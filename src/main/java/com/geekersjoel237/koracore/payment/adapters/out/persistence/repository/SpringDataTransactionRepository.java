package com.geekersjoel237.koracore.payment.adapters.out.persistence.repository;

import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataTransactionRepository extends JpaRepository<TransactionEntity, String> {
}
