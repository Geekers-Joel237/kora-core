package com.geekersjoel237.koracore.payment.adapters.out.persistence.entities;

import com.geekersjoel237.koracore.payment.domain.enums.ResourceType;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import com.geekersjoel237.koracore.shared.adapters.out.persistence.entities.VersionedEntity;

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
public class AccountEntity extends VersionedEntity {

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

    public void update(Account.Snapshot snapshot) {
        this.setBalanceAmount(snapshot.balance().solde().value());
        this.setBalanceCurrency(snapshot.balance().solde().currency());
        this.setBlocked(snapshot.isBlocked());
    }
}
