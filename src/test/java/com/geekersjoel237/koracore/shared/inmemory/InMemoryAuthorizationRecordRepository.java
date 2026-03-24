package com.geekersjoel237.koracore.shared.inmemory;

import com.geekersjoel237.koracore.domain.model.AuthorizationRecord;
import com.geekersjoel237.koracore.domain.model.AuthorizationRecord.AuthorizationStatus;
import com.geekersjoel237.koracore.domain.port.AuthorizationRecordRepository;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryAuthorizationRecordRepository implements AuthorizationRecordRepository {

    private final Map<String, AuthorizationRecord> store = new HashMap<>();

    @Override
    public void save(AuthorizationRecord record) {
        store.put(record.transactionId().value(), record);
    }

    /**
     * Returns a record whose status is ACTIVE, regardless of TTL.
     * The TTL check is the service's responsibility (via {@code authRecord.isActive()}).
     */
    @Override
    public Optional<AuthorizationRecord> findActiveByTransactionId(Id transactionId) {
        AuthorizationRecord record = store.get(transactionId.value());
        if (record != null && record.snapshot().status() == AuthorizationStatus.ACTIVE) {
            return Optional.of(record);
        }
        return Optional.empty();
    }

    @Override
    public List<AuthorizationRecord> findExpiredActive(Instant now) {
        return store.values().stream()
                .filter(r -> r.snapshot().status() == AuthorizationStatus.ACTIVE
                        && r.snapshot().expiresAt().isBefore(now))
                .toList();
    }

    /** Test utility: load any record for a transaction, regardless of status. */
    public Optional<AuthorizationRecord> findByTransactionId(Id transactionId) {
        return Optional.ofNullable(store.get(transactionId.value()));
    }
}