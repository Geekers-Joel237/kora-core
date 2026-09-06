package com.geekersjoel237.koracore.payment.ports.in;

import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionHistoryQuery;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionSummary;
import com.geekersjoel237.koracore.shared.ports.in.QueryHandler;

/** Reads one page of a wallet's movements. */
public interface TransactionHistoryQueryHandler
        extends QueryHandler<TransactionHistoryQuery, PageResult<TransactionSummary>> {
}
