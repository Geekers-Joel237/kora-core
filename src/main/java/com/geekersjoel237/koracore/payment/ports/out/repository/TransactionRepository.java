package com.geekersjoel237.koracore.payment.ports.out.repository;

import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.util.Optional;

/**
 * The write side of the ledger, and only that.
 *
 * <p>Reading used to live here too, which is what made a page of history rebuild
 * twenty aggregates to render six fields each. Queries have their own port now, with
 * SQL shaped like the answer — see {@code TransactionQueryPort}.
 */
public interface TransactionRepository {

    void save(Transaction transaction);

    Optional<Transaction> findById(Id transactionId);
}