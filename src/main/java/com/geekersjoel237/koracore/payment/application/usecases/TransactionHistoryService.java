package com.geekersjoel237.koracore.payment.application.usecases;

import com.geekersjoel237.koracore.payment.application.query.history.HistoryCriteria;
import com.geekersjoel237.koracore.payment.application.query.history.HistoryFilter;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionHistoryQuery;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionKind;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionRow;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionSummary;
import com.geekersjoel237.koracore.payment.domain.enums.CustomerStatus;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.payment.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.payment.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.payment.ports.in.TransactionHistoryQueryHandler;
import com.geekersjoel237.koracore.payment.ports.out.query.ReadAccountRepository;
import com.geekersjoel237.koracore.payment.ports.out.query.ReadTransactionRepository;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.PhoneNumber;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

public final class TransactionHistoryService implements TransactionHistoryQueryHandler {

    private final ReadAccountRepository accountRepository;
    private final ReadTransactionRepository transactionRepository;
    private final ZoneId customerZone;

    public TransactionHistoryService(ReadAccountRepository accountRepository,
                                     ReadTransactionRepository transactionRepository,
                                     ZoneId customerZone) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.customerZone = customerZone;
    }

    @Override
    public PageResult<TransactionSummary> execute(TransactionHistoryQuery query) {
        Id walletId = accountRepository.findWalletId(query.customerId())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found for customer: " + query.customerId().value()));

        return transactionRepository.findPage(walletId, criteriaOf(query), query.pagination())
                .map(row -> toSummary(row, walletId));
    }

    private HistoryCriteria criteriaOf(TransactionHistoryQuery query) {
        HistoryFilter filter = query.filter();
        Set<TransactionState> states = filter.status() == null ? Set.of() : filter.status().states();
        return new HistoryCriteria(states, filter.kind(),
                startOf(filter.from()), startOf(filter.to() == null ? null : filter.to().plusDays(1)),
                query.after());
    }

    private Instant startOf(LocalDate day) {
        return day == null ? null : day.atStartOfDay(customerZone).toInstant();
    }

    private static TransactionSummary toSummary(TransactionRow row, Id walletId) {
        TransactionKind kind = TransactionKind.of(row.type(), row.fromAccountId().equals(walletId.value()));
        boolean isTransfer = row.type() == TransactionType.P2P_TRANSFER;
        return new TransactionSummary(
                new Id(row.transactionId()),
                row.transactionNumber(),
                kind,
                CustomerStatus.of(TransactionState.fromValue(row.state())),
                row.amount(),
                isTransfer ? null : row.paymentMethod(),
                isTransfer ? maskedCounterpartOf(row) : null,
                row.occurredAt());
    }

    // Masked: rendered in a client and copied into support tickets.
    private static String maskedCounterpartOf(TransactionRow row) {
        if (row.counterpartPhonePrefix() == null || row.counterpartPhoneNumber() == null) return null;
        return PhoneNumber.of(row.counterpartPhonePrefix(), row.counterpartPhoneNumber()).masked();
    }
}
