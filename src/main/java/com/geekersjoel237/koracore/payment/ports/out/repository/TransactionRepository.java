package com.geekersjoel237.koracore.payment.ports.out.repository;

import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.util.Optional;

public interface TransactionRepository {

    void save(Transaction transaction);

    Optional<Transaction> findById(Id transactionId);
}