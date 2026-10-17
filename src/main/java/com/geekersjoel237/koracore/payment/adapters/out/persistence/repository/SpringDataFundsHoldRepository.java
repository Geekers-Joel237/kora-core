package com.geekersjoel237.koracore.payment.adapters.out.persistence.repository;

import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.FundsHoldEntity;
import com.geekersjoel237.koracore.payment.domain.enums.HoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface SpringDataFundsHoldRepository extends JpaRepository<FundsHoldEntity, String> {

    Optional<FundsHoldEntity> findByTransactionIdAndStatus(String transactionId, HoldStatus status);

    @Query("SELECT COALESCE(SUM(h.amount), 0) FROM FundsHoldEntity h " +
           "WHERE h.accountId = :accountId AND h.currency = :currency AND h.status = :status")
    BigDecimal sumAmount(@Param("accountId") String accountId,
                         @Param("currency") String currency,
                         @Param("status") HoldStatus status);
}
