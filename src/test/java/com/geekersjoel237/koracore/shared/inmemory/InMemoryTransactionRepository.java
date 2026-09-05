package com.geekersjoel237.koracore.shared.inmemory;

import com.geekersjoel237.koracore.domain.enums.Direction;
import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.port.TransactionRepository;
import com.geekersjoel237.koracore.domain.query.PageRequest;
import com.geekersjoel237.koracore.domain.query.PageResult;
import com.geekersjoel237.koracore.domain.query.TransactionFilter;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.util.ArrayList;
import java.util.Comparator;
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
                .filter(tx -> tx.snapshot().fromAccountId().equals(accountId)
                        || tx.snapshot().toAccountId().equals(accountId))
                .toList();
    }

    @Override
    public PageResult<Transaction> findByAccountId(Id accountId, TransactionFilter filter, PageRequest pageRequest) {
        List<Transaction> matched = store.values().stream()
                .filter(tx -> matchesAccount(tx, accountId, filter.direction()))
                .filter(tx -> matchesFilter(tx, filter))
                .sorted(Comparator.comparing(tx -> tx.snapshot().createdAt(),
                        Comparator.reverseOrder()))
                .toList();

        int total = matched.size();
        int from = pageRequest.page() * pageRequest.size();
        List<Transaction> page = from >= total
                ? List.of()
                : matched.subList(from, Math.min(from + pageRequest.size(), total));

        return new PageResult<>(page, pageRequest.page(), pageRequest.size(), total);
    }

    private boolean matchesAccount(Transaction tx, Id accountId, Direction direction) {
        Transaction.Snapshot snap = tx.snapshot();
        if (direction == Direction.OUTBOUND) return snap.fromAccountId().equals(accountId);
        if (direction == Direction.INBOUND)  return snap.toAccountId().equals(accountId);
        return snap.fromAccountId().equals(accountId) || snap.toAccountId().equals(accountId);
    }

    private boolean matchesFilter(Transaction tx, TransactionFilter f) {
        Transaction.Snapshot snap = tx.snapshot();
        if (f.type()  != null && !snap.type().equals(f.type()))            return false;
        if (f.state() != null && !snap.state().name().equals(f.state()))   return false;
        if (f.from()  != null && snap.createdAt().isBefore(f.from()))      return false;
        if (f.to()    != null && snap.createdAt().isAfter(f.to()))         return false;
        return true;
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