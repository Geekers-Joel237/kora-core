package com.geekersjoel237.koracore.payment.ports.out.query;

import com.geekersjoel237.koracore.payment.application.query.history.TransactionFilter;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionRow;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.Pagination;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

/**
 * The read side of the ledger.
 *
 * <p>Separate from {@code TransactionRepository} on purpose: that one loads aggregates
 * to change them, this one shapes rows to show them. They share a schema and nothing
 * else — different statements, different result types, no transaction here.
 *
 * <p>Takes a {@link Pagination} rather than two loose ints, so the offset an adapter
 * computes is the one the query was validated against.
 */
public interface TransactionQueryPort {

    PageResult<TransactionRow> findPage(Id walletId, TransactionFilter filter, Pagination pagination);
}
