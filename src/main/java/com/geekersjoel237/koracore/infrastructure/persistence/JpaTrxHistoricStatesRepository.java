package com.geekersjoel237.koracore.infrastructure.persistence;

import com.geekersjoel237.koracore.domain.enums.TriggerSource;
import com.geekersjoel237.koracore.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.domain.port.TrxHistoricStatesRepository;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.infrastructure.persistence.entities.TrxStateHistoricEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.SpringDataTrxStateHistoricRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JpaTrxHistoricStatesRepository implements TrxHistoricStatesRepository {

    private final SpringDataTrxStateHistoricRepository jpaRepository;

    public JpaTrxHistoricStatesRepository(SpringDataTrxStateHistoricRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(TrxStateHistoric historic) {
        TrxStateHistoricEntity entity = TrxStateHistoricEntity.builder()
                .transactionId(historic.transactionId().value())
                .oldState(historic.oldState() != null ? historic.oldState().name() : null)
                .newState(historic.newState().name())
                .occurredAt(historic.occurredAt())
                .triggeredBy(historic.triggeredBy() != null ? historic.triggeredBy().name() : null)
                .correlationId(historic.correlationId() != null ? historic.correlationId().value() : null)
                .providerRef(historic.providerRef())
                .actorId(historic.actorId() != null ? historic.actorId().value() : null)
                .notes(historic.notes())
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
                        e.getOldState() != null ? TransactionState.fromValue(e.getOldState()) : null,
                        TransactionState.fromValue(e.getNewState()),
                        e.getOccurredAt(),
                        e.getTriggeredBy() != null ? TriggerSource.valueOf(e.getTriggeredBy()) : null,
                        e.getCorrelationId() != null ? new Id(e.getCorrelationId()) : null,
                        e.getProviderRef(),
                        e.getActorId() != null ? new Id(e.getActorId()) : null,
                        e.getNotes()
                ))
                .toList();
    }

}