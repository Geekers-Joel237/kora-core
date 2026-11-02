package com.geekersjoel237.koracore.payment.unit.doubles;

import com.geekersjoel237.koracore.payment.domain.model.LedgerEntry;
import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.payment.ports.out.repository.TransactionRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class InMemoryTransactionRepository implements TransactionRepository {

    private final Map<String, Transaction.Snapshot> store = new HashMap<>();
    private final InMemoryTrxHistoricStatesRepository historics;

    public InMemoryTransactionRepository(InMemoryTrxHistoricStatesRepository historics) {
        this.historics = historics;
    }

    @Override
    public void save(Transaction transaction) {
        store.put(transaction.snapshot().transactionId().value(), transaction.snapshot());
    }

    @Override
    public Optional<Transaction> findById(Id transactionId) {
        return Optional.ofNullable(store.get(transactionId.value()))
                .map(this::rebuild);
    }

    private Transaction rebuild(Transaction.Snapshot snap) {
        List<LedgerEntry> entries = snap.entries().stream()
                .map(LedgerEntry::createFromSnapshot)
                .toList();
        return Transaction.createFromSnapshot(snap, entries, historics.rowsOf(snap.transactionId()));
    }

    public List<Transaction> findAll() {
        return store.values().stream()
                .map(this::rebuild)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    public int count() {
        return store.size();
    }

    public void reset() {
        store.clear();
    }
}