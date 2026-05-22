package com.geekersjoel237.koracore.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.Getter;
import org.springframework.data.domain.Persistable;

@Getter
@MappedSuperclass
public abstract class VersionedEntity extends BaseEntity implements Persistable<String> {

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Instruct Spring Data to use only the ID for new-entity detection.
     * Without this override, Spring Data sees version=null (builder never sets it)
     * and calls em.persist() on every save — causing NonUniqueObjectException when
     * the same entity is saved more than once in a single Hibernate session.
     * With this override, Spring Data always calls em.merge() when ID is set,
     * which correctly handles both insert (new ID not in DB) and update (existing ID).
     */
    @Override
    @Transient
    public boolean isNew() {
        return getId() == null;
    }

}