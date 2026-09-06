package com.geekersjoel237.koracore.payment.adapters.out.persistence.entities;

import com.geekersjoel237.koracore.payment.domain.enums.LedgerEntryType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import com.geekersjoel237.koracore.shared.adapters.out.persistence.entities.BaseEntity;

@Entity
@Table(name = "ledger_entries",
        indexes = {
                @Index(name = "idx_ledger_entries_transaction_id", columnList = "transaction_id"),
                @Index(name = "idx_ledger_entries_account_id", columnList = "account_id"),
                @Index(name = "idx_ledger_entries_type", columnList = "type")
        }
)
@Getter
@Setter
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryEntity extends BaseEntity {

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private LedgerEntryType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}