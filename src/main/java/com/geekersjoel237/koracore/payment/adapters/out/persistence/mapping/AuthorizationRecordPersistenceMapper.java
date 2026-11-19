package com.geekersjoel237.koracore.payment.adapters.out.persistence.mapping;

import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.AuthorizationRecordEntity;
import com.geekersjoel237.koracore.payment.domain.model.AuthorizationRecord;
import com.geekersjoel237.koracore.shared.adapters.out.persistence.mapping.PersistenceMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = PersistenceMapperConfig.class)
public interface AuthorizationRecordPersistenceMapper {

    @Mapping(target = "authorizedAmount", source = "authorizedAmount.value")
    @Mapping(target = "currency", source = "authorizedAmount.currency")
    AuthorizationRecordEntity toEntity(AuthorizationRecord.Snapshot snapshot);

    @Mapping(target = "authorizedAmount.value", source = "authorizedAmount")
    @Mapping(target = "authorizedAmount.currency", source = "currency")
    AuthorizationRecord.Snapshot toSnapshot(AuthorizationRecordEntity entity);
}
