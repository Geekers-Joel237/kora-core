package com.geekersjoel237.koracore.infrastructure.persistence;

import com.geekersjoel237.koracore.domain.model.Operation;
import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.domain.port.TransactionRepository;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.infrastructure.persistence.entities.OperationEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.entities.TransactionEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.SpringDataTransactionRepository;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.SpringDataTrxStateHistoricRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
        jpaTransactionRepo.save(toEntity(transaction));
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
                        h.getOccurredAt()
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