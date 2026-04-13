package com.geekersjoel237.koracore.application.port.in;

import com.geekersjoel237.koracore.application.query.TransactionSummary;
import com.geekersjoel237.koracore.domain.query.PageResult;
import com.geekersjoel237.koracore.domain.query.TransactionFilter;
import com.geekersjoel237.koracore.domain.vo.Id;

public interface TransactionHistoryUseCase {

    /**
     * Returns the paginated transaction history for the authenticated customer.
     *
     * @param customerId the customer whose history is requested
     * @param filter     optional criteria (type, state, date range, direction)
     * @param page       0-based page number
     * @param size       page size (1–100)
     * @throws IllegalArgumentException if size > 100
     */
    PageResult<TransactionSummary> execute(Id customerId, TransactionFilter filter, int page, int size);
}