package com.geekersjoel237.koracore.payment.application.usecases;

import com.geekersjoel237.koracore.auth.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionHistoryQuery;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionRow;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionSummary;
import com.geekersjoel237.koracore.payment.domain.enums.Direction;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.payment.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.payment.ports.in.TransactionHistoryQueryHandler;
import com.geekersjoel237.koracore.payment.ports.out.query.AccountQueryPort;
import com.geekersjoel237.koracore.payment.ports.out.query.TransactionQueryPort;
import com.geekersjoel237.koracore.shared.domain.vo.Id;


public final class TransactionHistoryService implements TransactionHistoryQueryHandler {

    private final AccountQueryPort accounts;
    private final TransactionQueryPort transactions;

    public TransactionHistoryService(AccountQueryPort accounts, TransactionQueryPort transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Override
    public PageResult<TransactionSummary> execute(TransactionHistoryQuery query) {
        Id walletId = accounts.findWalletId(query.customerId())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found for customer: " + query.customerId().value()));

        return transactions.findPage(walletId, query.filter(), query.pagination())
                .map(row -> toSummary(row, walletId));
    }

    private TransactionSummary toSummary(TransactionRow row, Id walletId) {
        return new TransactionSummary(
                row.transactionId(),
                row.transactionNumber(),
                row.type(),
                directionFor(row, walletId),
                row.state(),
                row.amount(),
                row.paymentMethod(),
                counterpartFor(row),
                row.createdAt(),
                row.stateHistory());
    }

    private static Direction directionFor(TransactionRow row, Id walletId) {
        return row.fromAccountId().equals(walletId.value()) ? Direction.OUTBOUND : Direction.INBOUND;
    }

    /**
     * Only a transfer has a counterparty a customer would recognise; the other side of
     * a cash-in is the float account, which is Kora, not a person.
     *
     * <p>Masked, never raw: this string is rendered in a client and copied into support
     * tickets.
     */
    private static String counterpartFor(TransactionRow row) {
        if (row.type() != TransactionType.P2P_TRANSFER) return null;
        if (row.counterpartPhonePrefix() == null || row.counterpartPhoneNumber() == null) return null;

        return PhoneNumber.of(row.counterpartPhonePrefix(), row.counterpartPhoneNumber()).masked();
    }
}
