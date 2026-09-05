package com.geekersjoel237.koracore.application.service;

import com.geekersjoel237.koracore.application.command.CashInCommand;
import com.geekersjoel237.koracore.application.command.CashOutCommand;
import com.geekersjoel237.koracore.application.command.ReversePaymentCommand;
import com.geekersjoel237.koracore.application.command.TransferCommand;
import com.geekersjoel237.koracore.application.port.in.AuthUseCase;
import com.geekersjoel237.koracore.domain.SystemConstants;
import com.geekersjoel237.koracore.domain.enums.*;
import com.geekersjoel237.koracore.domain.exception.*;
import com.geekersjoel237.koracore.domain.model.*;
import com.geekersjoel237.koracore.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.domain.port.*;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.AuthorizationResult;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.domain.vo.PhoneNumber;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Objects;

/**
 * Orchestrates payment use cases via explicit micro-transactions.
 * <p>
 * Provider I/O (authorize, capture) runs between two short DB transactions,
 * holding zero database connections during network calls:
 * <pre>
 *   TX-1 (~10ms)  : validate context, persist INITIALIZED
 *   provider I/O  : authorize + capture (~1 400ms, no connection held)
 *   TX-2 (~20ms)  : reload account with lock, apply balance, persist COMPLETED
 * </pre>
 * P2P transfers and reversals involve no provider I/O and run in a single TX.
 */
@Service
public class PaymentTransactionalExecutor {

    private final TransactionTemplate txTemplate;
    private final AuthUseCase authUseCase;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final TrxHistoricStatesRepository historicRepo;
    private final ProviderPort provider;
    private final LedgerRepository ledgerRepository;
    private final AuthorizationRecordRepository authorizationRecordRepository;

    public PaymentTransactionalExecutor(PlatformTransactionManager txManager,
                                        AuthUseCase authUseCase,
                                        AccountRepository accountRepository,
                                        CustomerRepository customerRepository,
                                        TransactionRepository transactionRepository,
                                        TrxHistoricStatesRepository historicRepo,
                                        ProviderPort provider,
                                        LedgerRepository ledgerRepository,
                                        AuthorizationRecordRepository authorizationRecordRepository) {
        this.txTemplate = new TransactionTemplate(txManager);
        this.authUseCase = authUseCase;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.historicRepo = historicRepo;
        this.provider = provider;
        this.ledgerRepository = ledgerRepository;
        this.authorizationRecordRepository = authorizationRecordRepository;
    }

    // ── Cash In ───────────────────────────────────────────────────────────────

    public Transaction executeCashIn(CashInCommand cmd) {
        final Id correlationId = Id.generate();

        // BCrypt (~200ms) runs outside any DB transaction — holds zero connections.
        authUseCase.validatePin(cmd.customerId(), cmd.rawPin());

        // ── TX-1: validate context, persist INITIALIZED ───────────────────
        record Tx1Context(Transaction tx, PhoneNumber phone) {
        }

        Tx1Context ctx = Objects.requireNonNull(txTemplate.execute(status -> {
            ValidatedPayer payer = validatePayerNoLock(cmd.customerId());
            Account floatAccount = getSystemFloatAccount();
            Ledger ledger = ledgerRepository.findFirst();

            Transaction tx = ledger.initiate(floatAccount, payer.account(),
                    TransactionType.CASH_IN, cmd.paymentMethod(), cmd.amount());
            persistInitialState(tx);
            return new Tx1Context(tx, payer.customer().snapshot().phoneNumber());
        }));

        final Transaction tx = ctx.tx();

        // ── Provider authorize: no DB connection held ─────────────────────
        AuthorizationResult authResult;
        try {
            authResult = provider.authorize(cmd.amount(), cmd.paymentMethod(),
                    correlationId, ProviderOperationType.COLLECTION, ctx.phone());
        } catch (ProviderException e) {
            txTemplate.execute(status -> {
                tx.failAuthorization();
                flushHistorySince(tx, 1);
                transactionRepository.save(tx);
                return null;
            });
            return tx;
        }

        // ── Provider capture: no DB connection held ───────────────────────
        try {
            provider.capture(authResult.providerReference(), correlationId);
        } catch (ProviderException e) {
            txTemplate.execute(status -> {
                AuthorizationRecord authRecord = AuthorizationRecord.create(
                        tx.snapshot().transactionId(), authResult.providerReference(),
                        cmd.amount(), Duration.ofMinutes(15));
                authorizationRecordRepository.save(authRecord);
                tx.authorize();
                authRecord.cancel();
                authorizationRecordRepository.save(authRecord);
                tx.failCapture();
                flushHistorySince(tx, 1);
                transactionRepository.save(tx);
                return null;
            });
            return tx;
        }

        // ── TX-2: reload customer account with lock, apply balance, COMPLETED
        return txTemplate.execute(status -> {
            Account customerAccount = accountRepository
                    .findByCustomerIdForUpdate(cmd.customerId())
                    .orElseThrow(() -> new AccountNotFoundException(
                            "Account not found for customer: " + cmd.customerId().value()));
            Account floatAccount = getSystemFloatAccount();
            Ledger ledger = ledgerRepository.findFirst();

            AuthorizationRecord authRecord = AuthorizationRecord.create(
                    tx.snapshot().transactionId(), authResult.providerReference(),
                    cmd.amount(), Duration.ofMinutes(15));
            authorizationRecordRepository.save(authRecord);

            tx.authorize();
            tx.capture();
            ledger.writeEntries(tx, floatAccount, customerAccount, cmd.amount());
            applyBalanceUpdate(floatAccount, customerAccount, cmd.amount());
            authRecord.consume();
            authorizationRecordRepository.save(authRecord);

            tx.pendSettlement();
            tx.settle();
            tx.markCompleted();

            flushHistorySince(tx, 1);
            transactionRepository.save(tx);
            return tx;
        });
    }

    // ── Cash Out ──────────────────────────────────────────────────────────────

    public Transaction executeCashOut(CashOutCommand cmd) {
        final Id correlationId = Id.generate();

        // BCrypt (~200ms) runs outside any DB transaction — holds zero connections.
        authUseCase.validatePin(cmd.customerId(), cmd.rawPin());

        // ── TX-1: validate context (ledger.initiate checks sufficient funds),
        //          persist INITIALIZED ──────────────────────────────────────
        record Tx1Context(Transaction tx, PhoneNumber phone) {
        }

        Tx1Context ctx = Objects.requireNonNull(txTemplate.execute(status -> {
            ValidatedPayer payer = validatePayerNoLock(cmd.customerId());
            Account floatAccount = getSystemFloatAccount();
            Ledger ledger = ledgerRepository.findFirst();
            PhoneNumber phone = payer.customer().snapshot().phoneNumber();

            // ledger.initiate() calls requireSufficientFunds for CUSTOMER_ACCOUNT —
            // throws InsufficientFundsException before persistInitialState if balance < amount.
            Transaction tx = ledger.initiate(payer.account(), floatAccount,
                    TransactionType.CASH_OUT, cmd.paymentMethod(), cmd.amount());
            persistInitialState(tx);
            return new Tx1Context(tx, phone);
        }));

        final Transaction tx = ctx.tx();

        // ── Provider authorize: no DB connection held ─────────────────────
        AuthorizationResult authResult;
        try {
            authResult = provider.authorize(cmd.amount(), cmd.paymentMethod(),
                    correlationId, ProviderOperationType.DISBURSEMENT, ctx.phone());
        } catch (ProviderException e) {
            txTemplate.execute(status -> {
                tx.failAuthorization();
                flushHistorySince(tx, 1);
                transactionRepository.save(tx);
                return null;
            });
            return tx;
        }

        // ── Provider capture: no DB connection held ───────────────────────
        try {
            provider.capture(authResult.providerReference(), correlationId);
        } catch (ProviderException e) {
            txTemplate.execute(status -> {
                AuthorizationRecord authRecord = AuthorizationRecord.create(
                        tx.snapshot().transactionId(), authResult.providerReference(),
                        cmd.amount(), Duration.ofMinutes(15));
                authorizationRecordRepository.save(authRecord);
                tx.authorize();
                authRecord.cancel();
                authorizationRecordRepository.save(authRecord);
                tx.failCapture();
                flushHistorySince(tx, 1);
                transactionRepository.save(tx);
                return null;
            });
            return tx;
        }

        // ── TX-2: reload customer account with lock, apply balance, COMPLETED
        return txTemplate.execute(status -> {
            Account customerAccount = accountRepository
                    .findByCustomerIdForUpdate(cmd.customerId())
                    .orElseThrow(() -> new AccountNotFoundException(
                            "Account not found for customer: " + cmd.customerId().value()));
            Account floatAccount = getSystemFloatAccount();
            Ledger ledger = ledgerRepository.findFirst();

            AuthorizationRecord authRecord = AuthorizationRecord.create(
                    tx.snapshot().transactionId(), authResult.providerReference(),
                    cmd.amount(), Duration.ofMinutes(15));
            authorizationRecordRepository.save(authRecord);

            tx.authorize();
            tx.capture();
            ledger.writeEntries(tx, customerAccount, floatAccount, cmd.amount());
            applyBalanceUpdate(customerAccount, floatAccount, cmd.amount());
            authRecord.consume();
            authorizationRecordRepository.save(authRecord);

            tx.pendSettlement();
            tx.settle();
            tx.markCompleted();

            flushHistorySince(tx, 1);
            transactionRepository.save(tx);
            return tx;
        });
    }

    // ── Transfer ──────────────────────────────────────────────────────────────

    public Transaction executeTransfer(TransferCommand cmd) {
        // BCrypt (~200ms) runs outside any DB transaction — holds zero connections.
        authUseCase.validatePin(cmd.customerId(), cmd.rawPin());

        // No provider I/O — single TX with both account locks (short hold, no I/O).
        return txTemplate.execute(status -> {
            ValidatedPayer payer = validatePayerWithLock(cmd.customerId());
            Account toAccount = validateRecipientAndGetAccount(cmd.toPhoneNumber());
            Ledger ledger = ledgerRepository.findFirst();

            Transaction tx = ledger.initiate(payer.account(), toAccount,
                    TransactionType.P2P_TRANSFER, PaymentMethod.WALLET, cmd.amount());

            return executeInternalTransfer(tx, ledger, payer.account(), toAccount, cmd.amount());
        });
    }

    // ── Reversal ──────────────────────────────────────────────────────────────

    public Transaction executeReversePayment(ReversePaymentCommand cmd) {
        if (cmd.reason() == null || cmd.reason().isBlank()) {
            throw new IllegalArgumentException("Reason is required for reversal");
        }
        return txTemplate.execute(status -> reverseInTx(cmd));
    }

    // ── TTL expiry ────────────────────────────────────────────────────────────

    public void executeExpireAuthorizations(java.time.Instant now) {
        txTemplate.execute(status -> {
            expireInTx(now);
            return null;
        });
    }

    // ── Reversal logic (runs inside a single TX) ──────────────────────────────

    private Transaction reverseInTx(ReversePaymentCommand cmd) {
        Transaction tx = transactionRepository.findById(cmd.transactionId())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Transaction not found: " + cmd.transactionId().value()));

        TransactionState currentState = tx.snapshot().state();

        if (currentState == TransactionState.AUTHORIZED) {
            AuthorizationRecord authRecord = authorizationRecordRepository
                    .findActiveByTransactionId(cmd.transactionId())
                    .orElseThrow(() -> new IllegalStateException(
                            "No active authorization for transaction: " + cmd.transactionId().value()));

            provider.reverse(authRecord.snapshot().providerReference(),
                    tx.snapshot().amount(), cmd.correlationId());

            authRecord.cancel();
            authorizationRecordRepository.save(authRecord);

            tx.reverse();
            historicRepo.save(TrxStateHistoric.of(
                    tx.snapshot().transactionId(),
                    TransactionState.AUTHORIZED,
                    TransactionState.REVERSED,
                    TriggerSource.OPERATOR_ACTION,
                    cmd.correlationId(), null, cmd.actorId(), cmd.reason()));
            transactionRepository.save(tx);
            return tx;
        }

        if (currentState == TransactionState.CAPTURED
                || currentState == TransactionState.SETTLEMENT_PENDING
                || currentState == TransactionState.SETTLED
                || currentState == TransactionState.COMPLETED) {

            Account fromAccount = accountRepository.findById(tx.snapshot().fromAccountId())
                    .orElseThrow(() -> new AccountNotFoundException(
                            "Account not found: " + tx.snapshot().fromAccountId().value()));
            Account toAccount = accountRepository.findById(tx.snapshot().toAccountId())
                    .orElseThrow(() -> new AccountNotFoundException(
                            "Account not found: " + tx.snapshot().toAccountId().value()));

            Ledger ledger = ledgerRepository.findFirst();

            ledger.reverse(tx);
            reverseBalanceUpdate(fromAccount, toAccount, tx.snapshot().amount());

            tx.reverse();
            historicRepo.save(TrxStateHistoric.of(
                    tx.snapshot().transactionId(),
                    currentState,
                    TransactionState.REVERSED,
                    TriggerSource.OPERATOR_ACTION,
                    cmd.correlationId(), null, cmd.actorId(), cmd.reason()));
            transactionRepository.save(tx);
            return tx;
        }

        throw new InvalidStateTransitionException(currentState, TransactionState.REVERSED);
    }

    private void expireInTx(java.time.Instant now) {
        var expired = authorizationRecordRepository.findExpiredActive(now);
        for (AuthorizationRecord authRecord : expired) {
            authRecord.expire();
            authorizationRecordRepository.save(authRecord);

            transactionRepository.findById(authRecord.snapshot().transactionId())
                    .ifPresent(tx -> {
                        tx.failAuthorization();
                        historicRepo.save(TrxStateHistoric.of(
                                tx.snapshot().transactionId(),
                                TransactionState.AUTHORIZED,
                                TransactionState.AUTHORIZATION_FAILED,
                                TriggerSource.SYSTEM_JOB,
                                null, null, new Id("system-ttl-job"), "Authorization TTL expired"));
                        transactionRepository.save(tx);
                    });
        }
    }

    // ── Internal transfer (single TX, no provider) ────────────────────────────

    private Transaction executeInternalTransfer(Transaction tx, Ledger ledger,
                                                Account fromAccount, Account toAccount,
                                                Amount amount) {
        // Persist INITIALIZED once upfront — mirrors the TX-1 pattern used by cash ops.
        transactionRepository.save(tx);
        historicRepo.save(tx.history().getFirst());

        // All state transitions in memory — no intermediate saves.
        tx.authorize();
        ledger.writeEntries(tx, fromAccount, toAccount, amount);
        applyBalanceUpdate(fromAccount, toAccount, amount);
        tx.capture();
        tx.pendSettlement();
        tx.settle();
        tx.markCompleted();

        // Single flush at the end: 5 history entries + 1 final tx save (COMPLETED).
        // Reduces 6 × save(tx) to 2, cutting UPDATE round-trips on the transactions row.
        flushHistorySince(tx, 1);
        transactionRepository.save(tx);
        return tx;
    }

    // ── Balance helpers ───────────────────────────────────────────────────────

    private void applyBalanceUpdate(Account fromAccount, Account toAccount, Amount amount) {
        if (fromAccount.snapshot().accountType().resourceType() == ResourceType.CUSTOMER_ACCOUNT) {
            fromAccount.debit(amount);
            accountRepository.save(fromAccount);
        }
        if (toAccount.snapshot().accountType().resourceType() == ResourceType.CUSTOMER_ACCOUNT) {
            toAccount.credit(amount);
            accountRepository.save(toAccount);
        }
    }

    private void reverseBalanceUpdate(Account fromAccount, Account toAccount, Amount amount) {
        if (fromAccount.snapshot().accountType().resourceType() == ResourceType.CUSTOMER_ACCOUNT) {
            fromAccount.credit(amount);
            accountRepository.save(fromAccount);
        }
        if (toAccount.snapshot().accountType().resourceType() == ResourceType.CUSTOMER_ACCOUNT) {
            toAccount.debit(amount);
            accountRepository.save(toAccount);
        }
    }

    // ── Persistence helpers ───────────────────────────────────────────────────

    /**
     * Persists the INITIALIZED state and its history entry (index 0).
     * Called once in TX-1 for cash-in and cash-out.
     */
    private void persistInitialState(Transaction tx) {
        transactionRepository.save(tx);
        historicRepo.save(tx.history().getFirst());
    }

    /**
     * Saves all history entries accumulated in memory since the last TX commit.
     * Used in TX-2 (and failure TXs) to flush the entries produced by
     * in-memory state transitions (authorize, capture, etc.) that occurred
     * outside any database transaction.
     *
     * @param fromIndex first entry to save (typically 1, skipping INITIALIZED already persisted in TX-1)
     */
    private void flushHistorySince(Transaction tx, int fromIndex) {
        tx.history().subList(fromIndex, tx.history().size()).forEach(historicRepo::save);
    }

    // ── Validation helpers ────────────────────────────────────────────────────

    /**
     * Loads and validates the payer WITHOUT a pessimistic lock.
     * PIN is validated by the caller before entering the TX — BCrypt holds no connection.
     * Used in TX-1 of cash-in and cash-out where no balance write happens.
     * TX-2 reloads the account with {@link AccountRepository#findByCustomerIdForUpdate}
     * before applying the balance update.
     */
    private ValidatedPayer validatePayerNoLock(Id customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Customer not found: " + customerId.value()));

        if (customer.isSuspended())
            throw new AccountSuspendedException(
                    "Account suspended for customer: " + customerId.value());

        Account account = accountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found for customer: " + customerId.value()));

        return new ValidatedPayer(customer, account);
    }

    /**
     * Loads and validates the payer WITH a pessimistic write lock.
     * PIN is validated by the caller before entering the TX — BCrypt holds no connection.
     * Used in P2P transfer where a single TX holds the lock for the balance write.
     */
    private ValidatedPayer validatePayerWithLock(Id customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Customer not found: " + customerId.value()));

        if (customer.isSuspended())
            throw new AccountSuspendedException(
                    "Account suspended for customer: " + customerId.value());

        Account account = accountRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found for customer: " + customerId.value()));

        return new ValidatedPayer(customer, account);
    }

    private Account validateRecipientAndGetAccount(String toPhoneNumber) {
        Customer customerTo = customerRepository.findByPhoneNumber(toPhoneNumber)
                .orElseThrow(() -> new AccountNotFoundException("No account found for phone: " + toPhoneNumber));

        if (customerTo.isSuspended()) {
            throw new AccountSuspendedException("Recipient account is suspended: " + toPhoneNumber);
        }

        Account accountTo = accountRepository.findByCustomerIdForUpdate(customerTo.snapshot().customerId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found for recipient: " + customerTo.snapshot().customerId().value()));

        if (accountTo.snapshot().isBlocked()) {
            throw new AccountBlockedException("Recipient account is blocked: " + accountTo.snapshot().accountId().value());
        }

        return accountTo;
    }

    /**
     * Loads the system float account without a pessimistic lock.
     * The float account balance is never written (ADR-001) — its integrity
     * is maintained exclusively through double-entry ledger entries.
     * A read lock would serialize all cash-in/cash-out operations for no benefit.
     */
    private Account getSystemFloatAccount() {
        return accountRepository.findFloatByProviderId(SystemConstants.PROVIDER_ID)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Float account not found for provider: " + SystemConstants.PROVIDER_ID.value()));
    }

    private record ValidatedPayer(Customer customer, Account account) {
    }
}