package com.geekersjoel237.koracore.payment.unit.doubles;

import com.geekersjoel237.koracore.payment.domain.enums.HoldStatus;
import com.geekersjoel237.koracore.payment.domain.model.FundsHold;
import com.geekersjoel237.koracore.payment.ports.out.repository.FundsHoldRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryFundsHoldRepository implements FundsHoldRepository {

    private final Map<Id, FundsHold.Snapshot> store = new HashMap<>();

    @Override
    public void save(FundsHold hold) {
        FundsHold.Snapshot snapshot = hold.snapshot();
        store.put(snapshot.id(), snapshot);
    }

    @Override
    public Optional<FundsHold> findActiveByTransactionId(Id transactionId) {
        return store.values().stream()
                .filter(h -> h.transactionId().equals(transactionId) && h.status() == HoldStatus.ACTIVE)
                .findFirst()
                .map(FundsHold::createFromSnapshot);
    }

    Amount activeTotalOn(Id accountId, String currency) {
        return store.values().stream()
                .filter(h -> h.accountId().equals(accountId)
                        && h.status() == HoldStatus.ACTIVE
                        && h.amount().currency().equals(currency))
                .map(FundsHold.Snapshot::amount)
                .reduce(Amount.of(BigDecimal.ZERO, currency), Amount::add);
    }
}
