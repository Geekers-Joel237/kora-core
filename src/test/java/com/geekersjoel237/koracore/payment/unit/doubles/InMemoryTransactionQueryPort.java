package com.geekersjoel237.koracore.payment.unit.doubles;

import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.Pagination;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionFilter;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionRow;
import com.geekersjoel237.koracore.payment.ports.out.query.TransactionQueryPort;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.util.ArrayList;
import java.util.List;

/**
 * Hands back the rows it was given.
 *
 * <p>Deliberately does no filtering and no paging. Those are the SQL's job now, and a
 * double that reimplemented them in Java would test a second implementation nobody
 * ships — the trap the old in-memory repository fell into, where the page it returned
 * and the page Postgres returned agreed only by luck.
 *
 * <p>What it makes testable is the handler: given these rows, which direction, and
 * whose masked number.
 */
public class InMemoryTransactionQueryPort implements TransactionQueryPort {

    private final List<TransactionRow> rows = new ArrayList<>();
    private Id lastWalletId;
    private TransactionFilter lastFilter;
    private Pagination lastPagination;

    public void give(TransactionRow... given) {
        rows.addAll(List.of(given));
    }

    /** What the handler asked for, so a test can assert it resolved the wallet first. */
    public Id lastWalletId() {
        return lastWalletId;
    }

    public TransactionFilter lastFilter() {
        return lastFilter;
    }

    /** What the handler forwarded, so a test can assert paging travels untouched. */
    public Pagination lastPagination() {
        return lastPagination;
    }

    @Override
    public PageResult<TransactionRow> findPage(Id walletId, TransactionFilter filter,
                                               Pagination pagination) {
        this.lastWalletId = walletId;
        this.lastFilter = filter;
        this.lastPagination = pagination;
        return PageResult.of(List.copyOf(rows), pagination, rows.size());
    }
}
