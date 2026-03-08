package com.geekersjoel237.koracore.infrastructure.persistence.repository;

import com.geekersjoel237.koracore.domain.enums.ResourceType;
import com.geekersjoel237.koracore.infrastructure.persistence.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaAccountRepository extends JpaRepository<AccountEntity, String> {
    Optional<AccountEntity> findByResourceTypeAndResourceId(ResourceType resourceType, String resourceId);
}
