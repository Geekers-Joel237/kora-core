package com.geekersjoel237.koracore.application.service;

import com.geekersjoel237.koracore.application.command.CashInCommand;
import com.geekersjoel237.koracore.application.command.CashOutCommand;
import com.geekersjoel237.koracore.application.command.ReversePaymentCommand;
import com.geekersjoel237.koracore.application.command.TransferCommand;
import com.geekersjoel237.koracore.application.port.in.AuthUseCase;
import com.geekersjoel237.koracore.domain.SystemConstants;
import com.geekersjoel237.koracore.domain.enums.ResourceType;
import com.geekersjoel237.koracore.domain.enums.TransactionType;
import com.geekersjoel237.koracore.domain.enums.TriggerSource;
import com.geekersjoel237.koracore.domain.exception.*;
import com.geekersjoel237.koracore.domain.model.*;
import com.geekersjoel237.koracore.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.domain.port.*;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.AuthorizationResult;
import com.geekersjoel237.koracore.domain.vo.Id;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Executes one payment attempt inside a single, self-contained transaction.
 * <p>
 * Kept separate from {@link PaymentService} so that the outer retry loop
 * in {@code PaymentService} can call this bean through a Spring proxy,
 * giving each retry attempt a <em>fresh</em> transaction (and a fresh
 * Hibernate session). Retrying inside the same transaction would re-use the
 * corrupted session state produced by the failed optimistic-lock attempt.
 */
@Service
@Transactional
public class PaymentTransactionalExecutor {

    private final AuthUseCase authUseCase;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final TrxHistoricStatesRepository historicRepo;
    private final ProviderPort provider;
    private final LedgerRepository ledgerRepository;
    private final AuthorizationRecordRepository authorizationRecordRepository;

    public PaymentTransactionalExecutor(AuthUseCase authUseCase,
                                        AccountRepository accountRepository,
                                        CustomerRepository customerRepository,
                                        TransactionRepository transactionRepository,
                                        TrxHistoricStatesRepository historicRepo,
                                        ProviderPort provider,
                                        LedgerRepository ledgerRepository,
                                        AuthorizationRecordRepository authorizationRecordRepository) {
        this.authUseCase = authUseCase;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.historicRepo = historicRepo;
        this.provider = provider;
        this.ledgerRepository = ledgerRepository;
        this.authorizationRecordRepository = authorizationRecordRepository;
    }

    // ── payment flows ─────────────────────────────────────────────────────────

    public Transaction executeCashIn(CashInCommand cmd) {
        Account customerAccount = validatePayerAndGetAccount(cmd.customerId(), cmd.rawPin());
        Account floatAccount = getSystemFloatAccountForUpdate();
        Ledger ledger = ledgerRepository.findFirst();

        // fromAccount = float (provider inbound), toAccount = customer
        Transaction tx = ledger.initiate(floatAccount, customerAccount,
                TransactionType.CASH_IN, cmd.paymentMethod(), cmd.amount());

        return executePayment(tx, ledger, floatAccount, customerAccount,
                cmd.amount(), cmd.paymentMethod(), Id.generate().value());
    }

    public Transaction executeCashOut(CashOutCommand cmd) {
        Account customerAccount = validatePayerAndGetAccount(cmd.customerId(), cmd.rawPin());
        Account floatAccount = getSystemFloatAccountForUpdate();
        Ledger ledger = ledgerRepository.findFirst();

        // fromAccount = customer (funds leave wallet), toAccount = float (provider outbound)
        Transaction tx = ledger.initiate(customerAccount, floatAccount,
                TransactionType.CASH_OUT, cmd.paymentMethod(), cmd.amount());

        return executePayment(tx, ledger, customerAccount, floatAccount,
                cmd.amount(), cmd.paymentMethod(), Id.generate().value());
    }

    public Transaction executeTransfer(TransferCommand cmd) {
        Account fromAccount = validatePayerAndGetAccount(cmd.customerId(), cmd.rawPin());
        Account toAccount = validateRecipientAndGetAccount(cmd.toPhoneNumber());
        Ledger ledger = ledgerRepository.findFirst();

        // SelfTransferException is enforced in Transaction.create() via ledger.initiate()
        Transaction tx = ledger.initiate(fromAccount, toAccount,
                TransactionType.P2P_TRANSFER, cmd.paymentMethod(), cmd.amount());

        return executePayment(tx, ledger, fromAccount, toAccount,
                cmd.amount(), cmd.paymentMethod(), Id.generate().value());
    }

    public Transaction executeReversePayment(ReversePaymentCommand cmd) {
        if (cmd.reason() == null || cmd.reason().isBlank()) {
            throw new IllegalArgumentException("Reason is required for reversal");
        }

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
            || currentState == TransactionState.SETTLEMENT_PENDING) {
            Account customerAccount = accountRepository.findById(tx.snapshot().fromId())
                    .orElseThrow(() -> new AccountNotFoundException(
                            "Account not found: " + tx.snapshot().fromId().value()));
            Ledger ledger = ledgerRepository.findFirst();

            ledger.reverse(tx);
            customerAccount.credit(tx.snapshot().amount());
            accountRepository.save(customerAccount);

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

    // ── TTL expiry ────────────────────────────────────────────────────────────

    public void executeExpireAuthorizations(java.time.Instant now) {
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
                                null, null, "system-ttl-job", "Authorization TTL expired"));
                        transactionRepository.save(tx);
                    });
        }
    }

    // ── core payment orchestration ────────────────────────────────────────────

    /**
     * Executes the full payment lifecycle for a single transaction:
     * INITIALIZED → AUTHORIZED → CAPTURED → SETTLEMENT_PENDING → SETTLED → COMPLETED
     * <p>
     * On authorization failure  : INITIALIZED → AUTHORIZATION_FAILED
     * On capture failure        : AUTHORIZED  → CAPTURE_FAILED
     */
    private Transaction executePayment(Transaction tx, Ledger ledger,
                                       Account fromAccount, Account toAccount,
                                       Amount amount, String paymentMethod,
                                       String correlationId) {
        persistTransactionState(tx); // INITIALIZED

        // ── Step 1: authorize with provider ──────────────────────────────────
        AuthorizationResult authResult;
        try {
            authResult = provider.authorize(amount, paymentMethod, correlationId);
        } catch (ProviderException e) {
            tx.failAuthorization();
            persistTransactionState(tx); // AUTHORIZATION_FAILED
            return tx;
        }

        AuthorizationRecord authRecord = AuthorizationRecord.create(
                tx.snapshot().transactionId(),
                authResult.providerReference(),
                amount,
                Duration.ofMinutes(15));
        authorizationRecordRepository.save(authRecord);
        tx.authorize();
        persistTransactionState(tx); // AUTHORIZED

        // ── Step 2: capture with provider ────────────────────────────────────
        try {
            provider.capture(authResult.providerReference(), correlationId);
        } catch (ProviderException e) {
            tx.failCapture();
            authRecord.cancel();
            authorizationRecordRepository.save(authRecord);
            persistTransactionState(tx); // CAPTURE_FAILED
            return tx;
        }

        // ── Step 3: write ledger entries and update balances ──────────────────
        ledger.writeEntries(tx, fromAccount, toAccount, amount);
        applyBalanceUpdate(fromAccount, toAccount, amount);
        authRecord.consume();
        authorizationRecordRepository.save(authRecord);
        tx.capture();
        persistTransactionState(tx); // CAPTURED

        tx.pendSettlement();
        persistTransactionState(tx); // SETTLEMENT_PENDING

        tx.settle();
        persistTransactionState(tx); // SETTLED

        tx.markCompleted();
        persistTransactionState(tx); // COMPLETED

        return tx;
    }

    /**
     * Updates account balances after a successful capture.
     * Only CUSTOMER_ACCOUNT balances are stored — FLOAT_ACCOUNT balance is always
     * audited through ledger operations, never through the stored balance (ADR-001).
     */
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

    // ── shared helpers ────────────────────────────────────────────────────────

    private Account validatePayerAndGetAccount(Id customerId, String pin) {
        authUseCase.validatePin(customerId, pin);

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new AccountNotFoundException("Customer not found: " + customerId.value()));

        if (customer.isSuspended()) {
            throw new AccountSuspendedException("Account suspended for customer: " + customerId.value());
        }

        return accountRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found for customer: " + customerId.value()));
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

    private Account getSystemFloatAccountForUpdate() {
        return accountRepository.findFloatByProviderIdForUpdate(SystemConstants.PROVIDER_ID)
                .orElseThrow(() -> new AccountNotFoundException("Float account not found for provider: " + SystemConstants.PROVIDER_ID.value()));
    }

    private void persistTransactionState(Transaction tx) {
        transactionRepository.save(tx);
        historicRepo.save(tx.history().getLast());
    }
}