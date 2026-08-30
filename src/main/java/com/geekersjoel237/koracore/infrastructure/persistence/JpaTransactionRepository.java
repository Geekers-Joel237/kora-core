package com.geekersjoel237.koracore.infrastructure.persistence;

import com.geekersjoel237.koracore.domain.enums.Direction;
import com.geekersjoel237.koracore.domain.model.Operation;
import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.domain.port.TransactionRepository;
import com.geekersjoel237.koracore.domain.query.PageRequest;
import com.geekersjoel237.koracore.domain.query.PageResult;
import com.geekersjoel237.koracore.domain.query.TransactionFilter;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.infrastructure.persistence.entities.OperationEntity;
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

            // Add any new operations added after INSERT (e.g., writeEntries in capturePayment).
            // Never clear or replace the collection — orphanRemoval would delete existing ops.
            Set<String> existingIds = entity.getOperations().stream()
                    .map(OperationEntity::getId)
                    .collect(Collectors.toSet());
            snap.operations().stream()
                    .filter(op -> !existingIds.contains(op.operationId().value()))
                    .forEach(op -> {
                        OperationEntity e = OperationEntity.builder()
                                .transactionId(snap.transactionId().value())
                                .type(op.type())
                                .amount(op.amount().value())
                                .currency(op.amount().currency())
                                .accountId(op.accountId().value())
                                .occurredAt(op.createdAt())
                                .build();
                        e.setId(op.operationId().value());
                        entity.getOperations().add(e);
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
                .findByFromIdOrToId(accountId.value(), accountId.value())
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
                predicates.add(cb.equal(root.get("fromId"), id));
            } else if (filter.direction() == Direction.INBOUND) {
                predicates.add(cb.equal(root.get("toId"), id));
            } else {
                predicates.add(cb.or(
                        cb.equal(root.get("fromId"), id),
                        cb.equal(root.get("toId"), id)
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

        List<OperationEntity> opEntities = snap.operations().stream()
                .map(op -> {
                    OperationEntity e = OperationEntity.builder()
                            .transactionId(snap.transactionId().value())
                            .type(op.type())
                            .amount(op.amount().value())
                            .currency(op.amount().currency())
                            .accountId(op.accountId().value())
                            .occurredAt(op.createdAt())
                            .build();
                    e.setId(op.operationId().value());
                    return e;
                })
                .toList();

        TransactionEntity entity = TransactionEntity.builder()
                .transactionNumber(snap.transactionNumber())
                .fromId(snap.fromId().value())
                .toId(snap.toId().value())
                .state(snap.state().name())
                .type(snap.type())
                .paymentMethod(snap.paymentMethod())
                .amount(snap.amount().value())
                .currency(snap.amount().currency())
                .occurredAt(snap.createdAt())
                .operations(opEntities)
                .build();
        entity.setId(snap.transactionId().value());
        return entity;
    }

    private Transaction toDomain(TransactionEntity entity) {
        List<Operation> operations = entity.getOperations().stream()
                .map(op -> Operation.createFromSnapshot(new Operation.Snapshot(
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
                        h.getTriggeredBy(),
                        h.getCorrelationId() != null ? new Id(h.getCorrelationId()) : null,
                        h.getProviderRef(),
                        h.getActorId() != null ? new Id(h.getActorId()) : null,
                        h.getNotes()
                ))
                .toList();

        Transaction.Snapshot snap = new Transaction.Snapshot(
                new Id(entity.getId()),
                entity.getTransactionNumber(),
                new Id(entity.getFromId()),
                new Id(entity.getToId()),
                TransactionState.fromValue(entity.getState()),
                entity.getType(),
                entity.getPaymentMethod(),
                new Amount(entity.getAmount(), entity.getCurrency()),
                entity.getOccurredAt(),
                operations.stream().map(Operation::snapshot).toList(),
                history
        );

        return Transaction.createFromSnapshot(snap, operations, history);
    }

}