package com.geekersjoel237.koracore.domain.port;

import com.geekersjoel237.koracore.domain.model.AuthorizationRecord;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuthorizationRecordRepository {
    void save(AuthorizationRecord record);
    Optional<AuthorizationRecord> findActiveByTransactionId(Id transactionId);
    List<AuthorizationRecord> findExpiredActive(Instant now);
}