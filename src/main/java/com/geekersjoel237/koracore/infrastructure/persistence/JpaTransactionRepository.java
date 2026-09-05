package com.geekersjoel237.koracore.infrastructure.persistence;

import com.geekersjoel237.koracore.domain.enums.Direction;
import com.geekersjoel237.koracore.domain.enums.TriggerSource;
import com.geekersjoel237.koracore.domain.model.LedgerEntry;
import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.domain.port.TransactionRepository;
import com.geekersjoel237.koracore.domain.query.PageRequest;
import com.geekersjoel237.koracore.domain.query.PageResult;
import com.geekersjoel237.koracore.domain.query.TransactionFilter;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.infrastructure.persistence.entities.LedgerEntryEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.entities.TransactionEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.SpringDataTransactionRepository;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.SpringDataTrxStateHistoricRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
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

    @Override
    public List<Transaction> findByAccountId(Id accountId) {
        return jpaTransactionRepo
                .findByFromAccountIdOrToAccountId(accountId.value(), accountId.value())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public PageResult<Transaction> findByAccountId(Id accountId, TransactionFilter filter, PageRequest pageRequest) {
        Specification<TransactionEntity> spec = buildSpec(accountId, filter);

        org.springframework.data.domain.PageRequest springPage =
                org.springframework.data.domain.PageRequest.of(
                        pageRequest.page(),
                        pageRequest.size(),
                        Sort.by(Sort.Direction.DESC, "occurredAt"));

        Page<TransactionEntity> page = jpaTransactionRepo.findAll(spec, springPage);

        List<Transaction> content = page.getContent().stream()
                .map(this::toDomain)
                .toList();

        return new PageResult<>(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    private Specification<TransactionEntity> buildSpec(Id accountId, TransactionFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            String id = accountId.value();
            if (filter.direction() == Direction.OUTBOUND) {
                predicates.add(cb.equal(root.get("fromAccountId"), id));
            } else if (filter.direction() == Direction.INBOUND) {
                predicates.add(cb.equal(root.get("toAccountId"), id));
            } else {
                predicates.add(cb.or(
                        cb.equal(root.get("fromAccountId"), id),
                        cb.equal(root.get("toAccountId"), id)
                ));
            }

            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.state() != null) {
                predicates.add(cb.equal(root.get("state"), filter.state()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), filter.to()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
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