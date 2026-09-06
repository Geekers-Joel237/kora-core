package com.geekersjoel237.koracore.payment.adapters.out.persistence;

import com.geekersjoel237.koracore.payment.domain.enums.TriggerSource;
import com.geekersjoel237.koracore.payment.domain.model.LedgerEntry;
import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.payment.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.payment.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.payment.ports.out.repository.TransactionRepository;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.LedgerEntryEntity;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.TransactionEntity;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.repository.SpringDataTransactionRepository;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.repository.SpringDataTrxStateHistoricRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
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

    public JpaTransactionRepository(SpringDataTransactionRepository jpaTransactionRepo,
                                    SpringDataTrxStateHistoricRepository jpaHistoricRepo) {
        this.jpaTransactionRepo = jpaTransactionRepo;
        this.jpaHistoricRepo = jpaHistoricRepo;
    }

    @Override
    public void save(Transaction transaction) {
        String id = transaction.snapshot().transactionId().value();

        TransactionEntity entity = jpaTransactionRepo.findById(id).orElse(null);
        if (entity == null) {
            jpaTransactionRepo.save(toEntity(transaction));
        } else {
            Transaction.Snapshot snap = transaction.snapshot();
            entity.setState(snap.state().name());

            // Add any new entries added after INSERT (e.g., writeEntries in capturePayment).
            // Never clear or replace the collection — orphanRemoval would delete existing ops.
            Set<String> existingIds = entity.getEntries().stream()
                    .map(LedgerEntryEntity::getId)
                    .collect(Collectors.toSet());
            snap.entries().stream()
                    .filter(op -> !existingIds.contains(op.entryId().value()))
                    .forEach(op -> {
                        LedgerEntryEntity e = LedgerEntryEntity.builder()
                                .transactionId(snap.transactionId().value())
                                .type(op.type())
                                .amount(op.amount().value())
                                .currency(op.amount().currency())
                                .accountId(op.accountId().value())
                                .occurredAt(op.createdAt())
                                .build();
                        e.setId(op.entryId().value());
                        entity.getEntries().add(e);
                    });
            jpaTransactionRepo.save(entity);
        }
    }

    @Override
    public Optional<Transaction> findById(Id transactionId) {
        return jpaTransactionRepo.findById(transactionId.value())
                .map(this::toDomain);
    }

    private TransactionEntity toEntity(Transaction tx) {
        Transaction.Snapshot snap = tx.snapshot();

        List<LedgerEntryEntity> entryEntities = snap.entries().stream()
                .map(op -> {
                    LedgerEntryEntity e = LedgerEntryEntity.builder()
                            .transactionId(snap.transactionId().value())
                            .type(op.type())
                            .amount(op.amount().value())
                            .currency(op.amount().currency())
                            .accountId(op.accountId().value())
                            .occurredAt(op.createdAt())
                            .build();
                    e.setId(op.entryId().value());
                    return e;
                })
                .toList();

        TransactionEntity entity = TransactionEntity.builder()
                .transactionNumber(snap.transactionNumber())
                .fromAccountId(snap.fromAccountId().value())
                .toAccountId(snap.toAccountId().value())
                .state(snap.state().name())
                .type(snap.type())
                .paymentMethod(snap.paymentMethod())
                .amount(snap.amount().value())
                .currency(snap.amount().currency())
                .occurredAt(snap.createdAt())
                .entries(entryEntities)
                .build();
        entity.setId(snap.transactionId().value());
        return entity;
    }

    private Transaction toDomain(TransactionEntity entity) {
        List<LedgerEntry> entries = entity.getEntries().stream()
                .map(op -> LedgerEntry.createFromSnapshot(new LedgerEntry.Snapshot(
                        new Id(op.getId()),
                        op.getType(),
                        new Amount(op.getAmount(), op.getCurrency()),
                        new Id(op.getAccountId()),
                        op.getOccurredAt()
                )))
                .toList();

        List<TrxStateHistoric> history = jpaHistoricRepo
                .findByTransactionIdOrderByOccurredAtAsc(entity.getId())
                .stream()
                .map(h -> new TrxStateHistoric(
                        new Id(h.getId()),
                        new Id(h.getTransactionId()),
                        h.getOldState() != null ? TransactionState.fromValue(h.getOldState()) : null,
                        TransactionState.fromValue(h.getNewState()),
                        h.getOccurredAt(),
                        h.getTriggeredBy() != null ? TriggerSource.valueOf(h.getTriggeredBy()) : null,
                        h.getCorrelationId() != null ? new Id(h.getCorrelationId()) : null,
                        h.getProviderRef(),
                        h.getActorId() != null ? new Id(h.getActorId()) : null,
                        h.getNotes()
                ))
                .toList();

        Transaction.Snapshot snap = new Transaction.Snapshot(
                new Id(entity.getId()),
                entity.getTransactionNumber(),
                new Id(entity.getFromAccountId()),
                new Id(entity.getToAccountId()),
                TransactionState.fromValue(entity.getState()),
                entity.getType(),
                entity.getPaymentMethod(),
                new Amount(entity.getAmount(), entity.getCurrency()),
                entity.getOccurredAt(),
                entries.stream().map(LedgerEntry::snapshot).toList(),
                history
        );

        return Transaction.createFromSnapshot(snap, entries, history);
    }

}