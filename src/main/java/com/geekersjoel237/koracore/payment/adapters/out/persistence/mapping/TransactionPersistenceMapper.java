package com.geekersjoel237.koracore.payment.adapters.out.persistence.mapping;

import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.LedgerEntryEntity;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.TransactionEntity;
import com.geekersjoel237.koracore.payment.domain.model.LedgerEntry;
import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.payment.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.shared.adapters.out.persistence.mapping.PersistenceMapperConfig;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = PersistenceMapperConfig.class, uses = TransactionStateMapper.class)
public interface TransactionPersistenceMapper {

    @Mapping(target = "id", source = "transactionId")
    @Mapping(target = "amount", source = "amount.value")
    @Mapping(target = "currency", source = "amount.currency")
    @Mapping(target = "occurredAt", source = "createdAt")
    @Mapping(target = "entries", ignore = true)
    TransactionEntity toEntity(Transaction.Snapshot snapshot);

    @Mapping(target = "id", source = "entry.entryId")
    @Mapping(target = "transactionId", source = "transactionId")
    @Mapping(target = "type", source = "entry.type")
    @Mapping(target = "amount", source = "entry.amount.value")
    @Mapping(target = "currency", source = "entry.amount.currency")
    @Mapping(target = "accountId", source = "entry.accountId")
    @Mapping(target = "occurredAt", source = "entry.createdAt")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    LedgerEntryEntity toEntity(LedgerEntry.Snapshot entry, Id transactionId);

    @Mapping(target = "transactionId", source = "entity.id")
    @Mapping(target = "transactionNumber", source = "entity.transactionNumber")
    @Mapping(target = "fromAccountId", source = "entity.fromAccountId")
    @Mapping(target = "toAccountId", source = "entity.toAccountId")
    @Mapping(target = "state", source = "entity.state")
    @Mapping(target = "type", source = "entity.type")
    @Mapping(target = "paymentMethod", source = "entity.paymentMethod")
    @Mapping(target = "amount.value", source = "entity.amount")
    @Mapping(target = "amount.currency", source = "entity.currency")
    @Mapping(target = "createdAt", source = "entity.occurredAt")
    @Mapping(target = "entries", source = "entity.entries")
    @Mapping(target = "history", source = "history")
    Transaction.Snapshot toSnapshot(TransactionEntity entity, List<TrxStateHistoric> history);

    @Mapping(target = "entryId", source = "id")
    @Mapping(target = "amount.value", source = "amount")
    @Mapping(target = "amount.currency", source = "currency")
    @Mapping(target = "createdAt", source = "occurredAt")
    LedgerEntry.Snapshot toSnapshot(LedgerEntryEntity entity);
}
