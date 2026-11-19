package com.geekersjoel237.koracore.payment.adapters.out.persistence.mapping;

import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.FundsHoldEntity;
import com.geekersjoel237.koracore.payment.domain.model.FundsHold;
import com.geekersjoel237.koracore.shared.adapters.out.persistence.mapping.PersistenceMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = PersistenceMapperConfig.class)
public interface FundsHoldPersistenceMapper {

    @Mapping(target = "amount", source = "amount.value")
    @Mapping(target = "currency", source = "amount.currency")
    FundsHoldEntity toEntity(FundsHold.Snapshot snapshot);

    @Mapping(target = "amount.value", source = "amount")
    @Mapping(target = "amount.currency", source = "currency")
    FundsHold.Snapshot toSnapshot(FundsHoldEntity entity);
}
