package com.geekersjoel237.koracore.payment.unit.doubles;

import com.geekersjoel237.koracore.payment.application.query.history.HistoryCriteria;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionRow;
import com.geekersjoel237.koracore.payment.ports.out.query.ReadTransactionRepository;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.Pagination;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.util.ArrayList;
import java.util.List;

/**
 * Deliberately no filtering: that is the SQL's job, proved in JdbcTransactionQueryAdapterTest.
 * Reimplementing it here would test a second implementation.
 */
public class InMemoryReadTransactionRepository implements ReadTransactionRepository {

    private final List<TransactionRow> rows = new ArrayList<>();
    private Id lastWalletId;
    private HistoryCriteria lastCriteria;
    private Pagination lastPagination;

    public void give(TransactionRow... given) {
        rows.addAll(List.of(given));
    }

    public Id lastWalletId() {
        return lastWalletId;
    }

    public HistoryCriteria lastCriteria() {
        return lastCriteria;
    }

    public Pagination lastPagination() {
        return lastPagination;
    }

    @Override
    public PageResult<TransactionRow> findPage(Id walletId, HistoryCriteria criteria, Pagination pagination) {
        this.lastWalletId = walletId;
        this.lastCriteria = criteria;
        this.lastPagination = pagination;
        return PageResult.fromFetched(List.copyOf(rows), pagination);
    }
}
