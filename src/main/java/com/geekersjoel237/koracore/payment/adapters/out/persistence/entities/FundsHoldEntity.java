package com.geekersjoel237.koracore.payment.adapters.out.persistence.entities;

import com.geekersjoel237.koracore.payment.domain.enums.HoldStatus;
import com.geekersjoel237.koracore.shared.adapters.out.persistence.entities.VersionedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "funds_holds")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundsHoldEntity extends VersionedEntity {

    @Column(name = "account_id", nullable = false, updatable = false)
    private String accountId;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private String transactionId;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private HoldStatus status;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
