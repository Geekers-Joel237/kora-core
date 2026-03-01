package com.geekersjoel237.koracore.infrastructure.persistence.entity;

import com.geekersjoel237.koracore.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "trx_state_historics",
        indexes = @Index(name = "idx_trx_state_historics_transaction_id", columnList = "transaction_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrxStateHistoricEntity extends BaseEntity {

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "old_state")
    private String oldState;

    @Column(name = "new_state", nullable = false)
    private String newState;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}