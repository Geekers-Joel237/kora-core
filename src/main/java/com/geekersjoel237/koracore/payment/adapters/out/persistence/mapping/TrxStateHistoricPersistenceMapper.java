package com.geekersjoel237.koracore.payment.adapters.out.persistence.mapping;

import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.TrxStateHistoricEntity;
import com.geekersjoel237.koracore.payment.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.shared.adapters.out.persistence.mapping.PersistenceMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = PersistenceMapperConfig.class, uses = TransactionStateMapper.class)
public interface TrxStateHistoricPersistenceMapper {

    TrxStateHistoricEntity toEntity(TrxStateHistoric historic);

    TrxStateHistoric toHistoric(TrxStateHistoricEntity entity);
}
