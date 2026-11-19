package com.geekersjoel237.koracore.payment.adapters.out.persistence;

import com.geekersjoel237.koracore.payment.adapters.out.persistence.mapping.TrxStateHistoricPersistenceMapper;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.repository.SpringDataTrxStateHistoricRepository;
import com.geekersjoel237.koracore.payment.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.payment.ports.out.repository.TrxHistoricStatesRepository;
import org.springframework.stereotype.Repository;

@Repository
public class JpaTrxHistoricStatesRepository implements TrxHistoricStatesRepository {

    private final SpringDataTrxStateHistoricRepository jpaRepository;
    private final TrxStateHistoricPersistenceMapper mapper;

    public JpaTrxHistoricStatesRepository(SpringDataTrxStateHistoricRepository jpaRepository,
                                          TrxStateHistoricPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(TrxStateHistoric historic) {
        jpaRepository.save(mapper.toEntity(historic));
    }
}
