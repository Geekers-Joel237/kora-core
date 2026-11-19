package com.geekersjoel237.koracore.payment.adapters.out.persistence;

import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.FundsHoldEntity;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.mapping.FundsHoldPersistenceMapper;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.repository.SpringDataFundsHoldRepository;
import com.geekersjoel237.koracore.payment.domain.enums.HoldStatus;
import com.geekersjoel237.koracore.payment.domain.model.FundsHold;
import com.geekersjoel237.koracore.payment.ports.out.repository.FundsHoldRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaFundsHoldRepository implements FundsHoldRepository {

    private final SpringDataFundsHoldRepository jpaRepository;
    private final FundsHoldPersistenceMapper mapper;

    public JpaFundsHoldRepository(SpringDataFundsHoldRepository jpaRepository, FundsHoldPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(FundsHold hold) {
        FundsHold.Snapshot snapshot = hold.snapshot();
        FundsHoldEntity entity = jpaRepository.findById(snapshot.id().value()).orElse(null);
        if (entity == null) {
            entity = mapper.toEntity(snapshot);
        } else {
            entity.update(snapshot.status(), snapshot.resolvedAt());
        }
        jpaRepository.save(entity);
    }

    @Override
    public Optional<FundsHold> findActiveByTransactionId(Id transactionId) {
        return jpaRepository.findByTransactionIdAndStatus(transactionId.value(), HoldStatus.ACTIVE)
                .map(mapper::toSnapshot)
                .map(FundsHold::createFromSnapshot);
    }
}
