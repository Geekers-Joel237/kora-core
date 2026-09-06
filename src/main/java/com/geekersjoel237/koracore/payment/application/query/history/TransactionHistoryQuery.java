package com.geekersjoel237.koracore.payment.application.query.history;

import com.geekersjoel237.koracore.shared.application.cqrs.paging.PagedQuery;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.Pagination;
import com.geekersjoel237.koracore.shared.domain.vo.Id;


public record TransactionHistoryQuery(Id customerId, TransactionFilter filter, Pagination pagination)
        implements PagedQuery<TransactionSummary> {

    public TransactionHistoryQuery {
        if (customerId == null)
            throw new IllegalArgumentException("Customer id is required");
        if (filter == null)
            throw new IllegalArgumentException("Filter is required; use TransactionFilter.empty()");
        if (pagination == null)
            throw new IllegalArgumentException("Pagination is required; use Pagination.firstPage()");
    }
}
