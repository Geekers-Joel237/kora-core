package com.geekersjoel237.koracore.payment.application.usecases;

import com.geekersjoel237.koracore.payment.application.command.ReversePaymentCommand;
import com.geekersjoel237.koracore.payment.ports.in.ReversePaymentCommandHandler;
import com.geekersjoel237.koracore.payment.application.result.PaymentResult;
import com.geekersjoel237.koracore.payment.domain.enums.ResourceType;
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
import com.geekersjoel237.koracore.payment.ports.out.provider.ProviderPort;
import com.geekersjoel237.koracore.payment.ports.out.repository.TransactionRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.TrxHistoricStatesRepository;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;
import com.geekersjoel237.koracore.shared.application.transaction.RetryPolicy;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;


public final class ReversePaymentService implements ReversePaymentCommandHandler {

    private final TransactionBoundary boundary;
    private final RetryPolicy retry;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final TrxHistoricStatesRepository history;
    private final ProviderPort provider;
    private final LedgerRepository ledgers;
    private final AuthorizationRecordRepository authorizations;

    public ReversePaymentService(TransactionBoundary boundary, RetryPolicy retry,
                                 AccountRepository accounts, TransactionRepository transactions,
                                 TrxHistoricStatesRepository history, ProviderPort provider,
                                 LedgerRepository ledgers,
                                 AuthorizationRecordRepository authorizations) {
        this.boundary = boundary;
        this.retry = retry;
        this.accounts = accounts;
        this.transactions = transactions;
        this.history = history;
        this.provider = provider;
        this.ledgers = ledgers;
        this.authorizations = authorizations;
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

    /**
     * Reads the transaction and decides which shape applies, refusing an illegal
     * state before anything else happens. Returns the provider reference only when a
     * hold has to be released.
     */
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

    /**
     * After the hold was released: cancel the record and close the transaction.
     */
    private PaymentResult recordRelease(ReversePaymentCommand cmd) {
        Transaction tx = reload(cmd.transactionId());

        AuthorizationRecord record = activeAuthorization(cmd.transactionId());
        record.cancel();
        authorizations.save(record);

        tx.reverse();
        recordTransition(cmd, tx, TransactionState.AUTHORIZED);
        transactions.save(tx);
        return PaymentResult.of(tx);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * The money moved: mirror it back, ledger entries and balance cache together.
     */
    private PaymentResult compensate(ReversePaymentCommand cmd) {
        Transaction tx = reload(cmd.transactionId());
        TransactionState state = tx.snapshot().state();
        if (!isPosted(state))
            throw new InvalidStateTransitionException(state, TransactionState.REVERSED);

        Account source = account(tx.snapshot().fromAccountId());
        Account destination = account(tx.snapshot().toAccountId());

        ledgers.findFirst().reverse(tx);
        reverseBalanceUpdate(source, destination, tx.snapshot().amount());

        tx.reverse();
        recordTransition(cmd, tx, state);
        transactions.save(tx);
        return PaymentResult.of(tx);
    }

    private Transaction reload(Id transactionId) {
        return transactions.findById(transactionId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Transaction not found: " + transactionId.value()));
    }

    private AuthorizationRecord activeAuthorization(Id transactionId) {
        return authorizations.findActiveByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalStateException(
                        "No active authorization for transaction: " + transactionId.value()));
    }

    private Account account(Id accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accountId.value()));
    }

    private void recordTransition(ReversePaymentCommand cmd, Transaction tx, TransactionState from) {
        history.save(TrxStateHistoric.of(
                tx.snapshot().transactionId(), from, TransactionState.REVERSED,
                TriggerSource.OPERATOR_ACTION, cmd.correlationId(), null,
                cmd.actorId(), cmd.reason()));
    }

    /**
     * The mirror of the posting update: what was debited is credited back.
     */
    private void reverseBalanceUpdate(Account from, Account to, Amount amount) {
        if (from.snapshot().accountType().resourceType() == ResourceType.CUSTOMER_ACCOUNT) {
            from.credit(amount);
            accounts.save(from);
        }
        if (to.snapshot().accountType().resourceType() == ResourceType.CUSTOMER_ACCOUNT) {
            to.debit(amount);
            accounts.save(to);
        }
    }

    /**
     * What TX-1 decided: a hold to release, or nothing but the amount to mirror.
     */
    private record Plan(String holdToRelease, Amount amount) {
    }
}
