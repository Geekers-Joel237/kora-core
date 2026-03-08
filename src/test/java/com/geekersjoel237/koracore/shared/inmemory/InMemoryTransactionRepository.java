package com.geekersjoel237.koracore.shared.inmemory;

import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.port.TransactionRepository;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryTransactionRepository implements TransactionRepository {

    private final Map<String, Transaction> store = new HashMap<>();

    @Override
    public void save(Transaction transaction) {
        store.put(transaction.snapshot().transactionId().value(), transaction);
    }

    @Override
    public Optional<Transaction> findById(Id transactionId) {
        return Optional.ofNullable(store.get(transactionId.value()));
    }

    @Override
    public List<Transaction> findByAccountId(Id accountId) {
        return store.values().stream()
                .filter(tx -> tx.snapshot().fromId().equals(accountId)
                        || tx.snapshot().toId().equals(accountId))
                .toList();
    }

    public List<Transaction> findAll() {
        return new ArrayList<>(store.values());
    }

    public int count() {
        return store.size();
    }

    public void reset() {
        store.clear();
    }
}