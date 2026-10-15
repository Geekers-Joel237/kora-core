package com.geekersjoel237.koracore.payment.application.query.history;

import com.geekersjoel237.koracore.shared.application.cqrs.paging.PagedQuery;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.Pagination;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

// after is null on the first page.
public record TransactionHistoryQuery(Id customerId, HistoryFilter filter, HistoryCursor after, Pagination pagination)
        implements PagedQuery<TransactionSummary> {

    public TransactionHistoryQuery {
        if (customerId == null)
            throw new IllegalArgumentException("Customer id is required");
        if (filter == null)
            throw new IllegalArgumentException("Filter is required; use HistoryFilter.empty()");
        if (pagination == null)
            throw new IllegalArgumentException("Pagination is required; use Pagination.firstPage()");
    }
}
