package com.geekersjoel237.koracore.domain.port;

import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository {
    void save(Transaction transaction);
    Optional<Transaction> findById(Id transactionId);
    List<Transaction> findByAccountId(Id accountId);
}