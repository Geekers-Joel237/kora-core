package com.geekersjoel237.koracore.payment.adapters.out.persistence;

import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.FundsHoldEntity;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.repository.SpringDataFundsHoldRepository;
import com.geekersjoel237.koracore.payment.domain.enums.HoldStatus;
import com.geekersjoel237.koracore.payment.domain.model.FundsHold;
import com.geekersjoel237.koracore.payment.ports.out.repository.FundsHoldRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaFundsHoldRepository implements FundsHoldRepository {

    private final SpringDataFundsHoldRepository jpaRepository;

    public JpaFundsHoldRepository(SpringDataFundsHoldRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(FundsHold hold) {
        FundsHold.Snapshot snap = hold.snapshot();
        FundsHoldEntity entity = jpaRepository.findById(snap.id().value())
                .orElseGet(() -> toEntity(snap));
        entity.setStatus(snap.status());
        entity.setResolvedAt(snap.resolvedAt());
        jpaRepository.save(entity);
    }

    @Override
    public Optional<FundsHold> findActiveByTransactionId(Id transactionId) {
        return jpaRepository.findByTransactionIdAndStatus(transactionId.value(), HoldStatus.ACTIVE)
                .map(JpaFundsHoldRepository::toDomain);
    }

    private static FundsHoldEntity toEntity(FundsHold.Snapshot snap) {
        FundsHoldEntity entity = FundsHoldEntity.builder()
                .accountId(snap.accountId().value())
                .transactionId(snap.transactionId().value())
                .amount(snap.amount().value())
                .currency(snap.amount().currency())
                .build();
        entity.setId(snap.id().value());
        return entity;
    }

    private static FundsHold toDomain(FundsHoldEntity entity) {
        return FundsHold.createFromSnapshot(new FundsHold.Snapshot(
                new Id(entity.getId()),
                new Id(entity.getAccountId()),
                new Id(entity.getTransactionId()),
                new Amount(entity.getAmount(), entity.getCurrency()),
                entity.getStatus(),
                entity.getResolvedAt()));
    }
}
