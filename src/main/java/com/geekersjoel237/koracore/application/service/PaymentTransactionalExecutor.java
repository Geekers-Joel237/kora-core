package com.geekersjoel237.koracore.application.service;

import com.geekersjoel237.koracore.application.command.AuthorizePaymentCommand;
import com.geekersjoel237.koracore.application.command.CapturePaymentCommand;
import com.geekersjoel237.koracore.application.command.CashInCommand;
import com.geekersjoel237.koracore.application.command.CashOutCommand;
import com.geekersjoel237.koracore.application.command.ReversePaymentCommand;
import com.geekersjoel237.koracore.application.command.TransferCommand;
import com.geekersjoel237.koracore.application.port.in.AuthUseCase;
import com.geekersjoel237.koracore.domain.SystemConstants;
import com.geekersjoel237.koracore.domain.enums.TransactionType;
import com.geekersjoel237.koracore.domain.enums.TriggerSource;
import com.geekersjoel237.koracore.domain.exception.AccountBlockedException;
import com.geekersjoel237.koracore.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.domain.exception.AccountSuspendedException;
import com.geekersjoel237.koracore.domain.exception.InvalidStateTransitionException;
import com.geekersjoel237.koracore.domain.exception.ProviderException;
import com.geekersjoel237.koracore.domain.model.Account;
import com.geekersjoel237.koracore.domain.model.AuthorizationRecord;
import com.geekersjoel237.koracore.domain.model.Customer;
import com.geekersjoel237.koracore.domain.model.Ledger;
import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.domain.port.*;
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

    // ── existing payment flows ────────────────────────────────────────────────

    public Transaction executeCashIn(CashInCommand cmd) {
        var customerAccount = validatePayerAndGetAccount(cmd.customerId(), cmd.rawPin());
        var floatAccount = getSystemFloatAccountForUpdate();
        var ledger = ledgerRepository.findFirst();

        var tx = ledger.cashIn(customerAccount, floatAccount, cmd.amount(), cmd.paymentMethod());

        return executePayment(tx, ledger,
                () -> provider.credit(cmd.amount(), cmd.paymentMethod()),
                () -> {
                    customerAccount.credit(cmd.amount());
                    accountRepository.save(customerAccount);
                });
    }

    public Transaction executeCashOut(CashOutCommand cmd) {
        var customerAccount = validatePayerAndGetAccount(cmd.customerId(), cmd.rawPin());
        var floatAccount = getSystemFloatAccountForUpdate();
        var ledger = ledgerRepository.findFirst();

        var tx = ledger.cashOut(customerAccount, floatAccount, cmd.amount(), cmd.paymentMethod());

        return executePayment(tx, ledger,
                () -> provider.debit(cmd.amount(), cmd.paymentMethod()),
                () -> {
                    customerAccount.debit(cmd.amount());
                    accountRepository.save(customerAccount);
                });
    }

    public Transaction executeTransfer(TransferCommand cmd) {
        var fromAccount = validatePayerAndGetAccount(cmd.customerId(), cmd.rawPin());
        var toAccount = validateRecipientAndGetAccount(cmd.toPhoneNumber());
        var ledger = ledgerRepository.findFirst();

        var tx = ledger.transfer(fromAccount, toAccount, cmd.amount(), cmd.paymentMethod());

        return executePayment(tx, ledger,
                () -> provider.send(cmd.amount(), cmd.paymentMethod()),
                () -> {
                    fromAccount.debit(cmd.amount());
                    toAccount.credit(cmd.amount());
                    accountRepository.save(fromAccount);
                    accountRepository.save(toAccount);
                });
    }

    // ── new lifecycle flows ───────────────────────────────────────────────────

    public Transaction executeAuthorizePayment(AuthorizePaymentCommand cmd) {
        Account customerAccount = validatePayerAndGetAccount(cmd.customerId(), cmd.rawPin());
        Account floatAccount = getSystemFloatAccount();
        Ledger ledger = ledgerRepository.findFirst();

        Transaction tx = ledger.initiate(customerAccount, floatAccount,
                TransactionType.CASH_OUT, cmd.paymentMethod(), cmd.amount());

        persistTransactionState(tx);    // INITIALIZED

        tx.authorize();

        try {
            var result = provider.authorize(cmd.amount(), cmd.paymentMethod(), cmd.correlationId());
            if (result.success()) {
                AuthorizationRecord authRecord = AuthorizationRecord.create(
                        tx.snapshot().transactionId(),
                        result.providerReference(),
                        cmd.amount(),
                        Duration.ofMinutes(15));
                authorizationRecordRepository.save(authRecord);
                persistTransactionState(tx); // AUTHORIZED
            }
        } catch (ProviderException e) {
            tx.failAuthorization();
            persistTransactionState(tx); // AUTHORIZATION_FAILED
        }

        return tx;
    }

    public Transaction executeCapturePayment(CapturePaymentCommand cmd) {
        Transaction tx = transactionRepository.findById(cmd.transactionId())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Transaction not found: " + cmd.transactionId().value()));

        if (tx.snapshot().state() != TransactionState.AUTHORIZED) {
            throw new InvalidStateTransitionException(tx.snapshot().state(), TransactionState.CAPTURED);
        }

        AuthorizationRecord authRecord = authorizationRecordRepository
                .findActiveByTransactionId(cmd.transactionId())
                .orElseThrow(() -> new IllegalStateException(
                        "No active authorization for transaction: " + cmd.transactionId().value()));

        if (!authRecord.isActive()) {
            tx.failAuthorization();
            authRecord.expire();
            authorizationRecordRepository.save(authRecord);
            persistTransactionState(tx);
            return tx;
        }

        try {
            provider.capture(authRecord.snapshot().providerReference(), cmd.correlationId());

            Account customerAccount = accountRepository.findByCustomerId(cmd.customerId())
                    .orElseThrow(() -> new AccountNotFoundException(
                            "Account not found for customer: " + cmd.customerId().value()));
            Account floatAccount = getSystemFloatAccountForUpdate();
            Ledger ledger = ledgerRepository.findFirst();

            ledger.writeEntries(tx, customerAccount, floatAccount, tx.snapshot().amount());
            customerAccount.debit(tx.snapshot().amount());
            accountRepository.save(customerAccount);

            authRecord.consume();
            authorizationRecordRepository.save(authRecord);

            tx.capture();
            persistTransactionState(tx); // CAPTURED

            tx.pendSettlement();
            persistTransactionState(tx); // SETTLEMENT_PENDING

        } catch (ProviderException e) {
            tx.failCapture();
            authRecord.cancel();
            authorizationRecordRepository.save(authRecord);
            persistTransactionState(tx); // CAPTURE_FAILED
        }

        return tx;
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
            // fromId is the customer account ID for CASH_OUT transactions
            Account customerAccount = accountRepository.findById(tx.snapshot().fromId())
                    .orElseThrow(() -> new AccountNotFoundException(
                            "Account not found: " + tx.snapshot().fromId().value()));
            Ledger ledger = ledgerRepository.findFirst();

            ledger.reverse(tx);
            customerAccount.credit(tx.snapshot().amount());
            accountRepository.save(customerAccount);

            TransactionState stateBeforeReverse = currentState;
            tx.reverse();
            historicRepo.save(TrxStateHistoric.of(
                    tx.snapshot().transactionId(),
                    stateBeforeReverse,
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
                                com.geekersjoel237.koracore.domain.model.state.TransactionState.AUTHORIZED,
                                com.geekersjoel237.koracore.domain.model.state.TransactionState.AUTHORIZATION_FAILED,
                                TriggerSource.SYSTEM_JOB,
                                null, null, "system-ttl-job", "Authorization TTL expired"));
                        transactionRepository.save(tx);
                    });
        }
    }

    // ── shared helpers ────────────────────────────────────────────────────────

    private Transaction executePayment(Transaction tx, Ledger ledger,
                                       Runnable providerAction, Runnable onSuccess) {
        persistTransactionState(tx);

        tx.authorize();
        persistTransactionState(tx);

        try {
            providerAction.run();

            tx.capture();
            persistTransactionState(tx);
            onSuccess.run();

            tx.pendSettlement();
            persistTransactionState(tx);

            tx.settle();
            persistTransactionState(tx);

            tx.markCompleted();
            persistTransactionState(tx);

        } catch (ProviderException e) {
            tx.markFailed();
            persistTransactionState(tx);
            var reverseTx = ledger.reverse(tx);
            transactionRepository.save(reverseTx);
        }

        return tx;
    }

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

    private Account getSystemFloatAccount() {
        return accountRepository.findFloatByProviderId(SystemConstants.PROVIDER_ID)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Float account not found for provider: " + SystemConstants.PROVIDER_ID.value()));
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