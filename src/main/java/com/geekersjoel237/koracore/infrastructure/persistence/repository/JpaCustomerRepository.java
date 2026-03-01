package com.geekersjoel237.koracore.infrastructure.persistence.repository;

import com.geekersjoel237.koracore.infrastructure.persistence.entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaCustomerRepository extends JpaRepository<CustomerEntity, String> {
    Optional<CustomerEntity> findByPhonePrefixAndPhoneNumber(String prefix, String number);
    Optional<CustomerEntity> findByUserEmail(String email);
    boolean existsByUserEmail(String email);

    @Query("SELECT c FROM CustomerEntity c WHERE CONCAT(c.phonePrefix, c.phoneNumber) = :fullNumber")
    Optional<CustomerEntity> findByFullPhoneNumber(@Param("fullNumber") String fullNumber);
}
