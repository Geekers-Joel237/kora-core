package com.geekersjoel237.koracore.payment.application.usecases;

import com.geekersjoel237.koracore.auth.domain.exception.CustomerNotFoundException;
import com.geekersjoel237.koracore.auth.domain.model.Customer;
import com.geekersjoel237.koracore.auth.ports.out.repository.CustomerRepository;
import com.geekersjoel237.koracore.auth.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.payment.application.command.CashOutCommand;
import com.geekersjoel237.koracore.payment.ports.in.CashOutCommandHandler;
import com.geekersjoel237.koracore.payment.ports.out.security.PinVerifier;
import com.geekersjoel237.koracore.payment.application.result.PaymentResult;
import com.geekersjoel237.koracore.payment.domain.SystemConstants;
import com.geekersjoel237.koracore.payment.domain.enums.ProviderOperationType;
import com.geekersjoel237.koracore.payment.domain.enums.ResourceType;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.payment.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.payment.domain.exception.AccountSuspendedException;
import com.geekersjoel237.koracore.payment.domain.exception.ProviderException;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.domain.model.AuthorizationRecord;
import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.payment.ports.out.repository.AccountRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.AuthorizationRecordRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.LedgerRepository;
import com.geekersjoel237.koracore.payment.ports.out.security.PinVerifier;
import com.geekersjoel237.koracore.payment.ports.out.provider.ProviderPort;
import com.geekersjoel237.koracore.payment.ports.out.repository.TransactionRepository;
import com.geekersjoel237.koracore.payment.ports.out.repository.TrxHistoricStatesRepository;
import com.geekersjoel237.koracore.payment.domain.vo.AuthorizationResult;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;
import com.geekersjoel237.koracore.shared.application.transaction.RetryPolicy;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.time.Duration;


public final class CashOutService implements CashOutCommandHandler {

    private static final Duration AUTHORIZATION_TTL = Duration.ofMinutes(15);

    private final TransactionBoundary boundary;
    private final RetryPolicy retry;
    private final PinVerifier pinVerifier;
    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final TransactionRepository transactions;
    private final TrxHistoricStatesRepository history;
    private final ProviderPort provider;
    private final LedgerRepository ledgers;
    private final AuthorizationRecordRepository authorizations;

    public CashOutService(TransactionBoundary boundary, RetryPolicy retry, PinVerifier pinVerifier,
                          AccountRepository accounts, CustomerRepository customers,
                          TransactionRepository transactions, TrxHistoricStatesRepository history,
                          ProviderPort provider, LedgerRepository ledgers,
                          AuthorizationRecordRepository authorizations) {
        this.boundary = boundary;
        this.retry = retry;
        this.pinVerifier = pinVerifier;
        this.accounts = accounts;
        this.customers = customers;
        this.transactions = transactions;
        this.history = history;
        this.provider = provider;
        this.ledgers = ledgers;
        this.authorizations = authorizations;
    }

    @Override
    public PaymentResult execute(CashOutCommand cmd) {
        Id correlationId = cmd.correlationId();

        pinVerifier.verify(cmd.customerId(), cmd.pin());

        Initiated initiated = boundary.execute(() -> initiate(cmd));

        AuthorizationResult authorization;
        try {
            authorization = provider.authorize(cmd.amount(), cmd.paymentMethod(),
                    correlationId, ProviderOperationType.DISBURSEMENT, initiated.phone());
        } catch (ProviderException e) {
            return boundary.execute(() -> failAuthorization(initiated.transactionId()));
        }

        try {
            provider.capture(authorization.providerReference(), correlationId);
        } catch (ProviderException e) {
            return boundary.execute(() -> failCapture(initiated.transactionId(), authorization, cmd));
        }

        return retry.execute(() ->
                boundary.execute(() -> settle(initiated.transactionId(), authorization, cmd)));
    }

    // ── TX-1 ──────────────────────────────────────────────────────────────────

    private Initiated initiate(CashOutCommand cmd) {
        Customer customer = customers.findById(cmd.customerId())
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Customer not found: " + cmd.customerId().value()));
        if (customer.isSuspended())
            throw new AccountSuspendedException(
                    "Account suspended for customer: " + cmd.customerId().value());

        // No lock here: TX-2 reloads the wallet with one before writing to it.
        // Ledger.initiate below refuses the movement if this balance is short.
        Account wallet = accounts.findByCustomerId(cmd.customerId())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found for customer: " + cmd.customerId().value()));
        Account floatAccount = loadFloatAccount();

        Transaction tx = ledgers.findFirst().initiate(wallet, floatAccount,
                TransactionType.CASH_OUT, cmd.paymentMethod(), cmd.amount());
        transactions.save(tx);
        history.save(tx.history().getFirst());

        return new Initiated(tx.snapshot().transactionId(), customer.snapshot().phoneNumber());
    }

    // ── TX-2 and its two failure siblings ────────────────────────────────────

    private PaymentResult settle(Id transactionId, AuthorizationResult authorization, CashOutCommand cmd) {
        Transaction tx = reload(transactionId);
        Account wallet = accounts.findByCustomerIdForUpdate(cmd.customerId())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found for customer: " + cmd.customerId().value()));
        Account floatAccount = loadFloatAccount();

        AuthorizationRecord record = AuthorizationRecord.create(
                transactionId, authorization.providerReference(), cmd.amount(), AUTHORIZATION_TTL);
        authorizations.save(record);

        tx.authorize();
        tx.capture();
        ledgers.findFirst().writeEntries(tx, wallet, floatAccount, cmd.amount());
        applyBalanceUpdate(wallet, floatAccount, cmd.amount());
        record.consume();
        authorizations.save(record);

        tx.pendSettlement();
        tx.settle();
        tx.markCompleted();

        flushHistorySince(tx);
        transactions.save(tx);
        return PaymentResult.of(tx);
    }

    private PaymentResult failAuthorization(Id transactionId) {
        Transaction tx = reload(transactionId);
        tx.failAuthorization();
        flushHistorySince(tx);
        transactions.save(tx);
        return PaymentResult.of(tx);
    }

    private PaymentResult failCapture(Id transactionId, AuthorizationResult authorization, CashOutCommand cmd) {
        Transaction tx = reload(transactionId);

        AuthorizationRecord record = AuthorizationRecord.create(
                transactionId, authorization.providerReference(), cmd.amount(), AUTHORIZATION_TTL);
        authorizations.save(record);
        tx.authorize();
        record.cancel();
        authorizations.save(record);
        tx.failCapture();

        flushHistorySince(tx);
        transactions.save(tx);
        return PaymentResult.of(tx);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Transaction reload(Id transactionId) {
        return transactions.findById(transactionId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Transaction not found: " + transactionId.value()));
    }

    private Account loadFloatAccount() {
        // No lock: the float balance is never written (ADR-001), so locking it would
        // serialize every cash-in and cash-out for nothing.
        return accounts.findFloatByProviderId(SystemConstants.PROVIDER_ID)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Float account not found for provider: " + SystemConstants.PROVIDER_ID.value()));
    }

    /**
     * The denormalized read cache, written in the same transaction as the ledger
     * entries (ADR-001, rule 9). The float is skipped: its stored balance stays at
     * zero by design and is audited through {@code ledger_entries}.
     */
    private void applyBalanceUpdate(Account from, Account to, Amount amount) {
        if (from.snapshot().accountType().resourceType() == ResourceType.CUSTOMER_ACCOUNT) {
            from.debit(amount);
            accounts.save(from);
        }
        if (to.snapshot().accountType().resourceType() == ResourceType.CUSTOMER_ACCOUNT) {
            to.credit(amount);
            accounts.save(to);
        }
    }

    /**
     * Index 0 was persisted by TX-1; everything after it happened in memory since.
     */
    private void flushHistorySince(Transaction tx) {
        tx.history().subList(1, tx.history().size()).forEach(history::save);
    }

    private record Initiated(Id transactionId, PhoneNumber phone) {
    }
}
