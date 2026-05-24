package com.geekersjoel237.koracore.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "trx_state_historics",
        indexes = @Index(name = "idx_trx_state_historics_transaction_id", columnList = "transaction_id"))
@Getter
@Setter
@ToString(callSuper = true)
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

    @Column(name = "triggered_by")
    private String triggeredBy;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "provider_ref")
    private String providerRef;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "notes")
    private String notes;
}