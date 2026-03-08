package com.geekersjoel237.koracore.infrastructure.persistence.entity;

import com.geekersjoel237.koracore.domain.enums.OperationType;
import com.geekersjoel237.koracore.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "operations",
        indexes = {
                @Index(name = "idx_operations_transaction_id", columnList = "transaction_id"),
                @Index(name = "idx_operations_account_id", columnList = "account_id"),
                @Index(name = "idx_operations_type ", columnList = "type")

        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationEntity extends BaseEntity {

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private OperationType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}