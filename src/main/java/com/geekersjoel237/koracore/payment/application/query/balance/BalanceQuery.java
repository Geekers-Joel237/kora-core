package com.geekersjoel237.koracore.payment.application.query.balance;

import com.geekersjoel237.koracore.shared.application.cqrs.Query;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

public record BalanceQuery(Id customerId) implements Query<BalanceResult> {
}
