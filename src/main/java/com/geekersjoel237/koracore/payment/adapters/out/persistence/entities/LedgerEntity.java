package com.geekersjoel237.koracore.payment.adapters.out.persistence.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import com.geekersjoel237.koracore.shared.adapters.out.persistence.entities.BaseEntity;

@Entity
@Table(name = "ledgers")
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
public class LedgerEntity extends BaseEntity {
    // The ledger is identified by its id (from BaseEntity).
    // Additional metadata (name, description) can be added here in future stages.
}