package com.geekersjoel237.koracore.infrastructure.persistence;

import com.geekersjoel237.koracore.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.domain.port.TrxHistoricStatesRepository;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.infrastructure.persistence.entity.TrxStateHistoricEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.JpaTrxStateHistoricRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PostgresTrxHistoricStatesRepository implements TrxHistoricStatesRepository {

    private final JpaTrxStateHistoricRepository jpaRepository;

    public PostgresTrxHistoricStatesRepository(JpaTrxStateHistoricRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(TrxStateHistoric historic) {
        TrxStateHistoricEntity entity = TrxStateHistoricEntity.builder()
                .transactionId(historic.transactionId().value())
                .oldState(historic.oldState() != null ? historic.oldState().name() : null)
                .newState(historic.newState().name())
                .occurredAt(historic.occurredAt())
                .build();
        entity.setId(historic.id().value());
        jpaRepository.save(entity);
    }

    @Override
    public List<TrxStateHistoric> findByTransactionId(Id transactionId) {
        return jpaRepository
                .findByTransactionIdOrderByOccurredAtAsc(transactionId.value())
                .stream()
                .map(e -> new TrxStateHistoric(
                        new Id(e.getId()),
                        new Id(e.getTransactionId()),
                        e.getOldState() != null ? stateFromName(e.getOldState()) : null,
                        stateFromName(e.getNewState()),
                        e.getOccurredAt()
                ))
                .toList();
    }

    private static TransactionState stateFromName(String name) {
        return switch (name) {
            case "INITIALIZED" -> TransactionState.INITIALIZED;
            case "PENDING"     -> TransactionState.PENDING;
            case "COMPLETED"   -> TransactionState.COMPLETED;
            case "FAILED"      -> TransactionState.FAILED;
            default -> throw new IllegalArgumentException("Unknown transaction state: " + name);
        };
    }
}