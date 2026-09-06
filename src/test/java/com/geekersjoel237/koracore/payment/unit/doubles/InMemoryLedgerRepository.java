package com.geekersjoel237.koracore.payment.unit.doubles;

import com.geekersjoel237.koracore.payment.domain.model.Ledger;
import com.geekersjoel237.koracore.payment.ports.out.repository.LedgerRepository;

public class InMemoryLedgerRepository implements LedgerRepository {

    private Ledger ledger;

    public InMemoryLedgerRepository(Ledger ledger) {
        this.ledger = ledger;
    }

    @Override
    public Ledger findFirst() {
        if (ledger == null) {
            throw new IllegalStateException("No Ledger found — check bootstrap");
        }
        return ledger;
    }

    public void reset(Ledger ledger) {
        this.ledger = ledger;
    }
}
