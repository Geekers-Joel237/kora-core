package com.geekersjoel237.koracore.shared.inmemory;

import com.geekersjoel237.koracore.domain.model.Ledger;
import com.geekersjoel237.koracore.domain.port.LedgerRepository;

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
