package com.geekersjoel237.koracore.infrastructure.persistence.entities;

import com.geekersjoel237.koracore.domain.enums.ResourceType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"resource_type", "resource_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_accounts_resource_id",
                        columnList = "resource_id"
                ),
                @Index(
                        name = "idx_accounts_resource_type",
                        columnList = "resource_type"
                ),
                @Index(
                        name = "idx_accounts_account_number",
                        columnList = "account_number"
                )
        }
)

@Getter
@Setter
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountEntity extends BaseEntity {

    @Column(name = "account_number", unique = true, nullable = false)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private ResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private String resourceId;

    @Column(name = "balance_amount", nullable = false)
    private BigDecimal balanceAmount;

    @Column(name = "balance_currency", nullable = false)
    private String balanceCurrency;

    @Column(name = "is_blocked", nullable = false)
    private boolean isBlocked;
}
