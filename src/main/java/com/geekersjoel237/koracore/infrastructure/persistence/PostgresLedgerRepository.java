package com.geekersjoel237.koracore.infrastructure.persistence;

import com.geekersjoel237.koracore.domain.model.Ledger;
import com.geekersjoel237.koracore.domain.port.LedgerRepository;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.infrastructure.persistence.entity.LedgerEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.JpaLedgerRepository;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresLedgerRepository implements LedgerRepository {

    private final JpaLedgerRepository jpaRepository;

    public PostgresLedgerRepository(JpaLedgerRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Ledger findFirst() {
        return jpaRepository.findFirstBy()
                .map(e -> Ledger.create(new Id(e.getId())))
                .orElseThrow(() -> new IllegalStateException("No Ledger found — check bootstrap"));
    }
}