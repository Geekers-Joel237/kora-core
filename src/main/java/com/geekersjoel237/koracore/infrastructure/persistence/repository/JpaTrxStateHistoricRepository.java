package com.geekersjoel237.koracore.infrastructure.persistence.repository;

import com.geekersjoel237.koracore.infrastructure.persistence.entity.TrxStateHistoricEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaTrxStateHistoricRepository extends JpaRepository<TrxStateHistoricEntity, String> {
    List<TrxStateHistoricEntity> findByTransactionIdOrderByOccurredAtAsc(String transactionId);
}