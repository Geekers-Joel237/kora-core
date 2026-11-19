package com.geekersjoel237.koracore.payment.adapters.out.persistence.mapping;

import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.AccountEntity;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.shared.adapters.out.persistence.mapping.PersistenceMapperConfig;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = PersistenceMapperConfig.class)
public interface AccountPersistenceMapper {

    @Mapping(target = "id", source = "accountId")
    @Mapping(target = "resourceType", source = "accountType.resourceType")
    @Mapping(target = "resourceId", source = "accountType.resourceId")
    @Mapping(target = "balanceAmount", source = "balance.amount.value")
    @Mapping(target = "balanceCurrency", source = "balance.amount.currency")
    @Mapping(target = "blocked", source = "isBlocked")
    AccountEntity toEntity(Account.Snapshot snapshot);

    @Mapping(target = "accountId", source = "entity.id")
    @Mapping(target = "accountNumber", source = "entity.accountNumber")
    @Mapping(target = "accountType.resourceId", source = "entity.resourceId")
    @Mapping(target = "accountType.resourceType", source = "entity.resourceType")
    @Mapping(target = "balance.amount.value", source = "entity.balanceAmount")
    @Mapping(target = "balance.amount.currency", source = "entity.balanceCurrency")
    @Mapping(target = "reserved", source = "reserved")
    @Mapping(target = "isBlocked", source = "entity.blocked")
    Account.Snapshot toSnapshot(AccountEntity entity, Amount reserved);
}
