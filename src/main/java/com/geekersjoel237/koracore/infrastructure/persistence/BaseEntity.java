package com.geekersjoel237.koracore.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Created on 28/02/2026
 *
 * @author Geekers_Joel237
 **/
@MappedSuperclass
@Getter
@Setter
@SoftDelete
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
