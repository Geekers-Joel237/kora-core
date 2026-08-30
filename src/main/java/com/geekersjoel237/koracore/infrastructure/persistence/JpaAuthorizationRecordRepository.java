package com.geekersjoel237.koracore.infrastructure.persistence;

import com.geekersjoel237.koracore.domain.model.AuthorizationRecord;
import com.geekersjoel237.koracore.domain.model.AuthorizationRecord.AuthorizationStatus;
import com.geekersjoel237.koracore.domain.port.AuthorizationRecordRepository;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.infrastructure.persistence.entities.AuthorizationRecordEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.SpringDataAuthorizationRecordRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaAuthorizationRecordRepository implements AuthorizationRecordRepository {

    private final SpringDataAuthorizationRecordRepository jpaRepository;

    public JpaAuthorizationRecordRepository(SpringDataAuthorizationRecordRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(AuthorizationRecord record) {
        AuthorizationRecord.Snapshot snap = record.snapshot();
        String id = snap.id().value();

        AuthorizationRecordEntity entity = jpaRepository.findById(id).orElse(null);
        if (entity == null) {
            entity = toEntity(record);
        } else {
            // Update the managed entity in place so its @Version is preserved.
            // Merging a newly-built detached entity (version=null) into a managed one
            // (version=N) causes Hibernate 6 to throw StaleObjectStateException immediately.
            entity.setStatus(snap.status());
        }
        jpaRepository.save(entity);
    }

    @Override
    public Optional<AuthorizationRecord> findActiveByTransactionId(Id transactionId) {
        return jpaRepository
                .findFirstByTransactionIdAndStatusAndDeletedAtIsNull(
                        transactionId.value(), AuthorizationStatus.ACTIVE)
                .map(this::toDomain);
    }

    @Override
    public List<AuthorizationRecord> findExpiredActive(Instant now) {
        return jpaRepository.findExpiredActive(AuthorizationStatus.ACTIVE, now)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private AuthorizationRecordEntity toEntity(AuthorizationRecord record) {
        AuthorizationRecord.Snapshot snap = record.snapshot();
        var entity = AuthorizationRecordEntity.builder()
                .transactionId(snap.transactionId().value())
                .providerReference(snap.providerReference())
                .authorizedAmount(snap.authorizedAmount().value())
                .currency(snap.authorizedAmount().currency())
                .authorizedAt(snap.authorizedAt())
                .expiresAt(snap.expiresAt())
                .status(snap.status())
                .build();
        entity.setId(snap.id().value());
        return entity;
    }

    private AuthorizationRecord toDomain(AuthorizationRecordEntity entity) {
        return AuthorizationRecord.createFromSnapshot(new AuthorizationRecord.Snapshot(
                new Id(entity.getId()),
                new Id(entity.getTransactionId()),
                entity.getProviderReference(),
                new Amount(entity.getAuthorizedAmount(), entity.getCurrency()),
                entity.getAuthorizedAt(),
                entity.getExpiresAt(),
                entity.getStatus()
        ));
    }
}