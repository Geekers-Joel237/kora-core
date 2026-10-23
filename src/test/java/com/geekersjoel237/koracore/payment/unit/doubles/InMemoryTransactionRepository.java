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

/**
 * Stores snapshots, not references.
 *
 * <p>Handing the caller back the same mutable instance it saved is not what a
 * repository does: a reload after a committed transaction rebuilds the aggregate
 * from what was written. A double that skips the rebuild lets a test pass while the
 * same code fails against a database — which is exactly the trap a saga retry falls
 * into, since it reloads and replays from the persisted state.
 */
public class InMemoryTransactionRepository implements TransactionRepository {

    private final Map<String, Transaction.Snapshot> store = new HashMap<>();

    @Override
    public void save(Transaction transaction) {
        store.put(transaction.snapshot().transactionId().value(), transaction.snapshot());
    }

    @Override
    public Optional<Transaction> findById(Id transactionId) {
        return Optional.ofNullable(store.get(transactionId.value()))
                .map(InMemoryTransactionRepository::rebuild);
    }

    private static Transaction rebuild(Transaction.Snapshot snap) {
        List<LedgerEntry> entries = snap.entries().stream()
                .map(LedgerEntry::createFromSnapshot)
                .toList();
        return Transaction.createFromSnapshot(snap, entries, snap.history());
    }

    public List<Transaction> findAll() {
        return store.values().stream()
                .map(InMemoryTransactionRepository::rebuild)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    public int count() {
        return store.size();
    }

    public void reset() {
        store.clear();
    }
}