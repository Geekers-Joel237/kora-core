package com.geekersjoel237.koracore.payment.ports.out.query;

import com.geekersjoel237.koracore.payment.application.query.balance.BalanceResult;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.util.Optional;


public interface AccountQueryPort {

    Optional<BalanceResult> findBalance(Id customerId);

    Optional<Id> findWalletId(Id customerId);
}
