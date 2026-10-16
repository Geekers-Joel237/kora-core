package com.geekersjoel237.koracore.payment.adapters.out.persistence;

import com.geekersjoel237.koracore.payment.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.payment.ports.out.repository.TrxHistoricStatesRepository;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.TrxStateHistoricEntity;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.repository.SpringDataTrxStateHistoricRepository;
import org.springframework.stereotype.Repository;

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
}