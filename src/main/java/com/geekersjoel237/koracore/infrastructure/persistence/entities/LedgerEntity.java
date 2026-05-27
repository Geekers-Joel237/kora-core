package com.geekersjoel237.koracore.infrastructure.persistence.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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