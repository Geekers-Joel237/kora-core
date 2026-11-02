package com.geekersjoel237.koracore.payment.ports.out.repository;

import com.geekersjoel237.koracore.payment.domain.model.FundsHold;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.util.Optional;

public interface FundsHoldRepository {
    void save(FundsHold hold);
    Optional<FundsHold> findActiveByTransactionId(Id transactionId);
}
