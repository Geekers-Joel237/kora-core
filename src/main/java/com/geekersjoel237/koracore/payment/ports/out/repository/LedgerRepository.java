package com.geekersjoel237.koracore.payment.ports.out.repository;

import com.geekersjoel237.koracore.payment.domain.model.Ledger;

public interface LedgerRepository {
    Ledger findFirst();
}
