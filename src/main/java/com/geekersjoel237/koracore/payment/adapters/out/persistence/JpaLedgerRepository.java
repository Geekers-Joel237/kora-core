package com.geekersjoel237.koracore.payment.adapters.out.persistence;

import com.geekersjoel237.koracore.payment.domain.model.Ledger;
import com.geekersjoel237.koracore.payment.ports.out.repository.LedgerRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.repository.SpringDataLedgerRepository;
import org.springframework.stereotype.Repository;

@Repository
public class JpaLedgerRepository implements LedgerRepository {

    private final SpringDataLedgerRepository jpaRepository;

    public JpaLedgerRepository(SpringDataLedgerRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Ledger findFirst() {
        return jpaRepository.findFirstBy()
                .map(e -> Ledger.create(new Id(e.getId())))
                .orElseThrow(() -> new IllegalStateException("No Ledger found — check bootstrap"));
    }
}