package com.geekersjoel237.koracore.application.service;

import com.geekersjoel237.koracore.application.port.in.TransactionHistoryUseCase;
import com.geekersjoel237.koracore.application.query.TransactionSummary;
import com.geekersjoel237.koracore.domain.enums.Direction;
import com.geekersjoel237.koracore.domain.enums.TransactionType;
import com.geekersjoel237.koracore.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.domain.model.Account;
import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.port.AccountRepository;
import com.geekersjoel237.koracore.domain.port.CustomerRepository;
import com.geekersjoel237.koracore.domain.port.TransactionRepository;
import com.geekersjoel237.koracore.domain.query.PageRequest;
import com.geekersjoel237.koracore.domain.query.PageResult;
import com.geekersjoel237.koracore.domain.query.TransactionFilter;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.domain.vo.PhoneNumber;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TransactionHistoryService implements TransactionHistoryUseCase {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public TransactionHistoryService(TransactionRepository transactionRepository,
                                     AccountRepository accountRepository,
                                     CustomerRepository customerRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    @Override
    public PageResult<TransactionSummary> execute(Id customerId, TransactionFilter filter,
                                                   int page, int size) {
        if (size > 100)
            throw new IllegalArgumentException("Page size cannot exceed 100, got: " + size);

        Account account = accountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found for customer: " + customerId.value()));

        Id customerAccountId = account.snapshot().accountId();
        PageResult<Transaction> txPage = transactionRepository.findByAccountId(
                customerAccountId, filter, new PageRequest(page, size));

        List<TransactionSummary> summaries = txPage.content().stream()
                .map(tx -> toSummary(tx.snapshot(), customerAccountId))
                .toList();

        return new PageResult<>(summaries, txPage.page(), txPage.size(), txPage.totalElements());
    }

    private TransactionSummary toSummary(Transaction.Snapshot tx, Id customerAccountId) {
        Direction direction = computeDirection(tx, customerAccountId);
        String counterpart = resolveCounterpart(tx, customerAccountId);

        List<TransactionSummary.StateEntry> history = tx.history().stream()
                .map(h -> new TransactionSummary.StateEntry(
                        h.snapshot().oldState(),
                        h.snapshot().newState(),
                        h.snapshot().occurredAt()))
                .toList();

        return new TransactionSummary(
                tx.transactionId().value(),
                tx.transactionNumber(),
                tx.type(),
                direction,
                tx.state().name(),
                tx.amount(),
                tx.paymentMethod(),
                counterpart,
                tx.createdAt(),
                history
        );
    }

    private Direction computeDirection(Transaction.Snapshot tx, Id customerAccountId) {
        return tx.fromId().equals(customerAccountId) ? Direction.OUTBOUND : Direction.INBOUND;
    }

    /**
     * Resolves the masked phone number of the counterpart for P2P transfers.
     * Returns null for CASH_IN and CASH_OUT (counterpart is the float account, not a customer).
     * Masking rule: prefix + first 3 digits of local number + "***" + last 3 digits.
     * Example: "+225" + "070" + "***" + "001" → "+225070***001"
     */
    private String resolveCounterpart(Transaction.Snapshot tx, Id customerAccountId) {
        if (tx.type() != TransactionType.P2P_TRANSFER) return null;

        Id counterpartAccountId = tx.fromId().equals(customerAccountId)
                ? tx.toId()
                : tx.fromId();

        return accountRepository.findById(counterpartAccountId)
                .flatMap(account -> {
                    Id counterpartCustomerId = account.snapshot().accountType().resourceId();
                    return customerRepository.findById(counterpartCustomerId);
                })
                .map(customer -> maskPhone(customer.snapshot().phoneNumber()))
                .orElse(null);
    }

    private static String maskPhone(PhoneNumber phoneNumber) {
        String local = phoneNumber.number();
        if (local.length() <= 6) return phoneNumber.fullNumber();
        return phoneNumber.prefix()
                + local.substring(0, 3)
                + "***"
                + local.substring(local.length() - 3);
    }
}