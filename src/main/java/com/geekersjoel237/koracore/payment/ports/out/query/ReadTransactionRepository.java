package com.geekersjoel237.koracore.payment.ports.out.query;

import com.geekersjoel237.koracore.payment.application.query.history.HistoryCriteria;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionRow;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.Pagination;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

public interface ReadTransactionRepository {
    PageResult<TransactionRow> findPage(Id walletId, HistoryCriteria criteria, Pagination pagination);
}
