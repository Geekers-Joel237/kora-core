package com.geekersjoel237.koracore.payment.adapters.out.persistence;

import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.AuthorizationRecordEntity;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.mapping.AuthorizationRecordPersistenceMapper;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.repository.SpringDataAuthorizationRecordRepository;
import com.geekersjoel237.koracore.payment.domain.model.AuthorizationRecord;
import com.geekersjoel237.koracore.payment.domain.model.AuthorizationRecord.AuthorizationStatus;
import com.geekersjoel237.koracore.payment.ports.out.repository.AuthorizationRecordRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaAuthorizationRecordRepository implements AuthorizationRecordRepository {

    private final SpringDataAuthorizationRecordRepository jpaRepository;
    private final AuthorizationRecordPersistenceMapper mapper;

    public JpaAuthorizationRecordRepository(SpringDataAuthorizationRecordRepository jpaRepository,
                                            AuthorizationRecordPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(AuthorizationRecord record) {
        AuthorizationRecord.Snapshot snapshot = record.snapshot();
        AuthorizationRecordEntity entity = jpaRepository.findById(snapshot.id().value()).orElse(null);
        if (entity == null) {
            entity = mapper.toEntity(snapshot);
        } else {
            entity.update(snapshot.status().name());
        }
        jpaRepository.save(entity);
    }

    @Override
    public Optional<AuthorizationRecord> findActiveByTransactionId(Id transactionId) {
        return jpaRepository
                .findFirstByTransactionIdAndStatusAndDeletedAtIsNull(
                        transactionId.value(), AuthorizationStatus.ACTIVE.name())
                .map(this::toDomain);
    }

    @Override
    public List<AuthorizationRecord> findExpiredActive(Instant now) {
        return jpaRepository.findExpiredActive(now)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private AuthorizationRecord toDomain(AuthorizationRecordEntity entity) {
        return AuthorizationRecord.createFromSnapshot(mapper.toSnapshot(entity));
    }
}
