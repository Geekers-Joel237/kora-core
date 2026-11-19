package com.geekersjoel237.koracore.auth.adapters.out.persistence.mapping;

import com.geekersjoel237.koracore.auth.adapters.out.persistence.entities.CustomerEntity;
import com.geekersjoel237.koracore.auth.domain.model.Customer;
import com.geekersjoel237.koracore.shared.adapters.out.persistence.mapping.PersistenceMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = PersistenceMapperConfig.class, uses = UserPersistenceMapper.class)
public interface CustomerPersistenceMapper {

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "phonePrefix", source = "phoneNumber.prefix")
    @Mapping(target = "phoneNumber", source = "phoneNumber.number")
    CustomerEntity toEntity(Customer.Snapshot snapshot);

    @Mapping(target = "customerId", source = "id")
    @Mapping(target = "phoneNumber.prefix", source = "phonePrefix")
    @Mapping(target = "phoneNumber.number", source = "phoneNumber")
    Customer.Snapshot toSnapshot(CustomerEntity entity);
}
