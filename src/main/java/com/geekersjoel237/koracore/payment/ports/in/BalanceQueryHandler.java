package com.geekersjoel237.koracore.payment.ports.in;

import com.geekersjoel237.koracore.payment.application.query.balance.BalanceResult;
import com.geekersjoel237.koracore.payment.application.query.balance.BalanceQuery;
import com.geekersjoel237.koracore.shared.ports.in.QueryHandler;

public interface BalanceQueryHandler extends QueryHandler<BalanceQuery, BalanceResult> {
}
