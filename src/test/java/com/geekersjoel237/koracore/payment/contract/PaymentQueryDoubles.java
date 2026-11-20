package com.geekersjoel237.koracore.payment.contract;

import com.geekersjoel237.koracore.payment.application.query.balance.BalanceQuery;
import com.geekersjoel237.koracore.payment.application.query.balance.BalanceResult;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionHistoryQuery;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionSummary;
import com.geekersjoel237.koracore.payment.ports.in.BalanceQueryHandler;
import com.geekersjoel237.koracore.payment.ports.in.TransactionHistoryQueryHandler;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
class PaymentQueryDoubles {

    static final class ProgrammableBalance extends ProgrammableQuery<BalanceQuery, BalanceResult>
            implements BalanceQueryHandler {
    }

    static final class ProgrammableHistory
            extends ProgrammableQuery<TransactionHistoryQuery, PageResult<TransactionSummary>>
            implements TransactionHistoryQueryHandler {
    }

    @Bean
    ProgrammableBalance programmableBalance() {
        return new ProgrammableBalance();
    }

    @Bean
    ProgrammableHistory programmableHistory() {
        return new ProgrammableHistory();
    }
}
