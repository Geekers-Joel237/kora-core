package com.geekersjoel237.koracore.payment.adapters.out.persistence.repository;

import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.TrxStateHistoricEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataTrxStateHistoricRepository extends JpaRepository<TrxStateHistoricEntity, String> {
    List<TrxStateHistoricEntity> findByTransactionIdOrderByOccurredAtAsc(String transactionId);
}