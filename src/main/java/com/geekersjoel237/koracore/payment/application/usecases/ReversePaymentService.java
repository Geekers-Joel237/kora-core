package com.geekersjoel237.koracore.payment.application.usecases;

import com.geekersjoel237.koracore.payment.application.command.ReversePaymentCommand;
import com.geekersjoel237.koracore.payment.ports.in.ReversePaymentCommandHandler;
import com.geekersjoel237.koracore.payment.application.result.PaymentResult;
import com.geekersjoel237.koracore.payment.domain.model.BalanceCache;
import com.geekersjoel237.koracore.payment.domain.enums.TriggerSource;
import com.geekersjoel237.koracore.payment.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.payment.domain.exception.InvalidStateTransitionException;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.domain.model.AuthorizationRecord;
import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.payment.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.payment.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.payment.ports.out.repository.AccountRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.AuthorizationRecordRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.LedgerRepository;
import com.geekersjoel237.koracore.payment.ports.out.provider.PaymentProviderPort;
import com.geekersjoel237.koracore.payment.ports.out.repository.TransactionRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.TrxHistoricStatesRepository;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;
import com.geekersjoel237.koracore.shared.application.transaction.RetryPolicy;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;


public final class ReversePaymentService implements ReversePaymentCommandHandler {

    private final TransactionBoundary boundary;
    private final RetryPolicy retry;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TrxHistoricStatesRepository trxHistoricStatesRepository;
    private final PaymentProviderPort provider;
    private final LedgerRepository ledgerRepository;
    private final AuthorizationRecordRepository authorizationRecordRepository;

    public ReversePaymentService(TransactionBoundary boundary, RetryPolicy retry,
                                 AccountRepository accountRepository, TransactionRepository transactionRepository,
                                 TrxHistoricStatesRepository trxHistoricStatesRepository, PaymentProviderPort provider,
                                 LedgerRepository ledgerRepository,
                                 AuthorizationRecordRepository authorizationRecordRepository) {
        this.boundary = boundary;
        this.retry = retry;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.trxHistoricStatesRepository = trxHistoricStatesRepository;
        this.provider = provider;
        this.ledgerRepository = ledgerRepository;
        this.authorizationRecordRepository = authorizationRecordRepository;
    }

    private static boolean isPosted(TransactionState state) {
        return state == TransactionState.CAPTURED
                || state == TransactionState.SETTLEMENT_PENDING
                || state == TransactionState.SETTLED
                || state == TransactionState.COMPLETED;
    }

    @Override
    public PaymentResult execute(ReversePaymentCommand cmd) {
        if (cmd.reason() == null || cmd.reason().isBlank())
            throw new IllegalArgumentException("Reason is required for reversal");

        Plan plan = boundary.execute(() -> classify(cmd));

        if (plan.holdToRelease() == null)
            return retry.execute(() -> boundary.execute(() -> compensate(cmd)));

        provider.reverse(plan.holdToRelease(), plan.amount(), cmd.correlationId());
        return retry.execute(() -> boundary.execute(() -> recordRelease(cmd)));
    }

    private Plan classify(ReversePaymentCommand cmd) {
        Transaction tx = reload(cmd.transactionId());
        TransactionState state = tx.snapshot().state();

        if (state == TransactionState.AUTHORIZED) {
            AuthorizationRecord record = activeAuthorization(cmd.transactionId());
            return new Plan(record.snapshot().providerReference(), tx.snapshot().amount());
        }
        if (isPosted(state))
            return new Plan(null, tx.snapshot().amount());

        throw new InvalidStateTransitionException(state, TransactionState.REVERSED);
    }

    private PaymentResult recordRelease(ReversePaymentCommand cmd) {
        Transaction tx = reload(cmd.transactionId());

        AuthorizationRecord record = activeAuthorization(cmd.transactionId());
        record.cancel();
        authorizationRecordRepository.save(record);

        tx.reverse();
        recordTransition(cmd, tx, TransactionState.AUTHORIZED);
        transactionRepository.save(tx);
        return PaymentResult.of(tx);
    }

    private PaymentResult compensate(ReversePaymentCommand cmd) {
        Transaction tx = reload(cmd.transactionId());
        TransactionState state = tx.snapshot().state();
        if (!isPosted(state))
            throw new InvalidStateTransitionException(state, TransactionState.REVERSED);

        Account source = account(tx.snapshot().fromAccountId());
        Account destination = account(tx.snapshot().toAccountId());

        ledgerRepository.findFirst().reverse(tx);
        reverseBalanceUpdate(source, destination, tx.snapshot().amount());

        tx.reverse();
        recordTransition(cmd, tx, state);
        transactionRepository.save(tx);
        return PaymentResult.of(tx);
    }

    private Transaction reload(Id transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Transaction not found: " + transactionId.value()));
    }

    private AuthorizationRecord activeAuthorization(Id transactionId) {
        return authorizationRecordRepository.findActiveByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalStateException(
                        "No active authorization for transaction: " + transactionId.value()));
    }

    private Account account(Id accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accountId.value()));
    }

    private void recordTransition(ReversePaymentCommand cmd, Transaction tx, TransactionState from) {
        trxHistoricStatesRepository.save(TrxStateHistoric.of(
                tx.snapshot().transactionId(), from, TransactionState.REVERSED,
                TriggerSource.OPERATOR_ACTION, cmd.correlationId(), null,
                cmd.actorId(), cmd.reason()));
    }

    private void reverseBalanceUpdate(Account from, Account to, Amount amount) {
        BalanceCache.reverse(from, to, amount).forEach(accountRepository::save);
    }

    private record Plan(String holdToRelease, Amount amount) {
    }
}
