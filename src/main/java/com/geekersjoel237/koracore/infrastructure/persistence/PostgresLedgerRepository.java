package com.geekersjoel237.koracore.infrastructure.persistence;

import com.geekersjoel237.koracore.domain.model.Ledger;
import com.geekersjoel237.koracore.domain.port.LedgerRepository;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.infrastructure.persistence.entity.LedgerEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.JpaLedgerRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresLedgerRepository implements LedgerRepository {

    private final JpaLedgerRepository jpaRepository;

    public PostgresLedgerRepository(JpaLedgerRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }


    @EventListener(ApplicationReadyEvent.class)
    public void ensureLedgerExists() {
        if (jpaRepository.findFirstBy().isEmpty()) {
            LedgerEntity entity = new LedgerEntity();
            entity.setId(Id.generate().value());
            jpaRepository.save(entity);
        }
    }

    @Override
    public Ledger findFirst() {
        return jpaRepository.findFirstBy()
                .map(e -> Ledger.create(new Id(e.getId())))
                .orElseThrow(() -> new IllegalStateException("No Ledger found — check bootstrap"));
    }
}