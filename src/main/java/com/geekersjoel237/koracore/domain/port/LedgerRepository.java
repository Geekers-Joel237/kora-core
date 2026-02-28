package com.geekersjoel237.koracore.domain.port;

import com.geekersjoel237.koracore.domain.model.Ledger;

public interface LedgerRepository {
    Ledger findFirst();
}
