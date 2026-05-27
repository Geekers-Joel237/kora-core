package com.geekersjoel237.koracore.infrastructure.persistence.repository;

import com.geekersjoel237.koracore.domain.enums.ResourceType;
import com.geekersjoel237.koracore.infrastructure.persistence.entities.AccountEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataAccountRepository extends JpaRepository<AccountEntity, String> {
    Optional<AccountEntity> findByResourceTypeAndResourceId(ResourceType resourceType, String resourceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountEntity a " +
           "WHERE a.resourceType = 'CUSTOMER_ACCOUNT' " +
           "AND a.resourceId = :customerId " +
           "AND a.deletedAt IS NULL")
    Optional<AccountEntity> findCustomerAccountForUpdate(@Param("customerId") String customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountEntity a " +
           "WHERE a.resourceType = 'FLOAT_ACCOUNT' " +
           "AND a.resourceId = :providerId " +
           "AND a.deletedAt IS NULL")
    Optional<AccountEntity> findFloatAccountForUpdate(@Param("providerId") String providerId);
}
