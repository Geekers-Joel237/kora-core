package com.geekersjoel237.koracore.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "authorization_records",
        indexes = {
                @Index(name = "idx_auth_records_transaction_id", columnList = "transaction_id"),
                @Index(name = "idx_auth_records_expires_at",     columnList = "expires_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationRecordEntity extends BaseEntity {

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "provider_reference", nullable = false)
    private String providerReference;

    @Column(name = "authorized_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal authorizedAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "authorized_at", nullable = false)
    private Instant authorizedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "status", nullable = false, length = 32)
    private String status;
}