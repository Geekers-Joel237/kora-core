package com.geekersjoel237.koracore.payment.adapters.out.persistence;

import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.LedgerEntryEntity;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.TransactionEntity;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.mapping.TransactionPersistenceMapper;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.mapping.TrxStateHistoricPersistenceMapper;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.repository.SpringDataTransactionRepository;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.repository.SpringDataTrxStateHistoricRepository;
import com.geekersjoel237.koracore.payment.domain.model.LedgerEntry;
import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.payment.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.payment.ports.out.repository.TransactionRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class JpaTransactionRepository implements TransactionRepository {

    private final SpringDataTransactionRepository jpaTransactionRepo;
    private final SpringDataTrxStateHistoricRepository jpaHistoricRepo;
    private final TransactionPersistenceMapper mapper;
    private final TrxStateHistoricPersistenceMapper historicMapper;

    public JpaTransactionRepository(SpringDataTransactionRepository jpaTransactionRepo,
                                    SpringDataTrxStateHistoricRepository jpaHistoricRepo,
                                    TransactionPersistenceMapper mapper,
                                    TrxStateHistoricPersistenceMapper historicMapper) {
        this.jpaTransactionRepo = jpaTransactionRepo;
        this.jpaHistoricRepo = jpaHistoricRepo;
        this.mapper = mapper;
        this.historicMapper = historicMapper;
    }

    @Override
    public void save(Transaction transaction) {
        Transaction.Snapshot snapshot = transaction.snapshot();
        TransactionEntity entity = jpaTransactionRepo.findById(snapshot.transactionId().value()).orElse(null);
        if (entity == null) {
            entity = mapper.toEntity(snapshot);
        } else {
            entity.update(snapshot.state().name());
        }

        // Append only: clearing or replacing the collection lets orphanRemoval delete existing entries.
        Set<String> existingIds = entity.getEntries().stream()
                .map(LedgerEntryEntity::getId)
                .collect(Collectors.toSet());
        snapshot.entries().stream()
                .filter(entry -> !existingIds.contains(entry.entryId().value()))
                .map(entry -> mapper.toEntity(entry, snapshot.transactionId()))
                .forEach(entity.getEntries()::add);
        jpaTransactionRepo.save(entity);
    }

    @Override
    public Optional<Transaction> findById(Id transactionId) {
        return jpaTransactionRepo.findById(transactionId.value())
                .map(this::toDomain);
    }

    private Transaction toDomain(TransactionEntity entity) {
        List<TrxStateHistoric> history = jpaHistoricRepo
                .findByTransactionIdOrderByOccurredAtAsc(entity.getId())
                .stream()
                .map(historicMapper::toHistoric)
                .toList();
        Transaction.Snapshot snapshot = mapper.toSnapshot(entity, history);
        List<LedgerEntry> entries = snapshot.entries().stream()
                .map(LedgerEntry::createFromSnapshot)
                .toList();
        return Transaction.createFromSnapshot(snapshot, entries, history);
    }
}
