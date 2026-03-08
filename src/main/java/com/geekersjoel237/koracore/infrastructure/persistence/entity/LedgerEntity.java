package com.geekersjoel237.koracore.infrastructure.persistence.entity;

import com.geekersjoel237.koracore.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ledgers")
@Data
@NoArgsConstructor
public class LedgerEntity extends BaseEntity {
    // The ledger is identified by its id (from BaseEntity).
    // Additional metadata (name, description) can be added here in future stages.
}