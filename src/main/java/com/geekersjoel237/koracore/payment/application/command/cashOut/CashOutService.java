package com.geekersjoel237.koracore.payment.application.command.cashOut;

import com.geekersjoel237.koracore.auth.domain.model.Customer;
import com.geekersjoel237.koracore.auth.ports.out.repository.CustomerRepository;
import com.geekersjoel237.koracore.payment.domain.SystemConstants;
import com.geekersjoel237.koracore.payment.domain.enums.ProviderOperationType;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.payment.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.payment.domain.exception.AccountSuspendedException;
import com.geekersjoel237.koracore.payment.domain.exception.PaymentProviderException;
import com.geekersjoel237.koracore.payment.domain.exception.PaymentTimeoutProviderException;
import com.geekersjoel237.koracore.payment.domain.model.*;
import com.geekersjoel237.koracore.payment.domain.vo.AuthorizationResult;
import com.geekersjoel237.koracore.payment.ports.in.CashOutCommandHandler;
import com.geekersjoel237.koracore.payment.ports.out.provider.PaymentProviderPort;
import com.geekersjoel237.koracore.payment.ports.out.repository.*;
import com.geekersjoel237.koracore.payment.ports.out.security.PinVerifier;
import com.geekersjoel237.koracore.shared.application.transaction.RetryPolicy;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;

import java.time.Duration;


public final class CashOutService implements CashOutCommandHandler {

    private static final Duration AUTHORIZATION_TTL = Duration.ofMinutes(15);

    private final TransactionBoundary boundary;
    private final RetryPolicy retry;
    private final PinVerifier pinVerifier;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final TrxHistoricStatesRepository trxHistoricStatesRepository;
    private final PaymentProviderPort provider;
    private final LedgerRepository ledgerRepository;
    private final AuthorizationRecordRepository authorizationRecordRepository;
    private final FundsHoldRepository holdRepository;

    public CashOutService(TransactionBoundary boundary, RetryPolicy retry, PinVerifier pinVerifier,
                          AccountRepository accountRepository, CustomerRepository customerRepository,
                          TransactionRepository transactionRepository, TrxHistoricStatesRepository trxHistoricStatesRepository,
                          PaymentProviderPort provider, LedgerRepository ledgerRepository,
                          AuthorizationRecordRepository authorizationRecordRepository,
                          FundsHoldRepository holdRepository) {
        this.boundary = boundary;
        this.retry = retry;
        this.pinVerifier = pinVerifier;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.trxHistoricStatesRepository = trxHistoricStatesRepository;
        this.provider = provider;
        this.ledgerRepository = ledgerRepository;
        this.authorizationRecordRepository = authorizationRecordRepository;
        this.holdRepository = holdRepository;
    }

    @Override
    public CashOutResult execute(CashOutCommand cmd) {
        Id correlationId = cmd.correlationId();

        pinVerifier.verify(cmd.customerId(), cmd.pin());

        Initiated initiated = boundary.execute(() -> initiate(cmd));

        AuthorizationResult authorization;
        try {
            authorization = provider.authorize(cmd.amount(), cmd.paymentMethod(),
                    correlationId, ProviderOperationType.DISBURSEMENT, initiated.phone());
        } catch (PaymentTimeoutProviderException e) {
            return boundary.execute(() -> recordAuthorizationUnknown(initiated.transactionId()));
        } catch (PaymentProviderException e) {
            return boundary.execute(() -> failAuthorization(initiated.transactionId()));
        }

        try {
            provider.capture(cmd.paymentMethod(), authorization.providerReference(), correlationId);
        } catch (PaymentTimeoutProviderException e) {
            return boundary.execute(() -> recordCaptureUnknown(initiated.transactionId(), authorization, cmd));
        } catch (PaymentProviderException e) {
            return boundary.execute(() -> failCapture(initiated.transactionId(), authorization, cmd));
        }

        return retry.execute(() ->
                boundary.execute(() -> settle(initiated.transactionId(), authorization, cmd)));
    }

    private Initiated initiate(CashOutCommand cmd) {
        Customer customer = customerRepository.findById(cmd.customerId())
                .orElseThrow();
        if (customer.isSuspended())
            throw new AccountSuspendedException(
                    "Account suspended for customer: " + cmd.customerId().value());

        Account wallet = accountRepository.findByCustomerIdForUpdate(cmd.customerId())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found for customer: " + cmd.customerId().value()));
        Account floatAccount = loadFloatAccount();

        Ledger ledger = ledgerRepository.findFirst();
        Transaction tx = ledger.initiate(wallet, floatAccount,
                TransactionType.CASH_OUT, cmd.paymentMethod(), cmd.amount());
        FundsHold hold = ledger.hold(tx, wallet);
        transactionRepository.save(tx);
        trxHistoricStatesRepository.save(tx.history().getFirst());
        holdRepository.save(hold);

        return new Initiated(tx.snapshot().transactionId(), customer.snapshot().phoneNumber());
    }

    private CashOutResult settle(Id transactionId, AuthorizationResult authorization, CashOutCommand cmd) {
        Transaction tx = reload(transactionId);
        Account wallet = accountRepository.findByCustomerIdForUpdate(cmd.customerId())
                .orElseThrow();
        Account floatAccount = loadFloatAccount();

        AuthorizationRecord record = AuthorizationRecord.create(
                transactionId, authorization.providerReference(), cmd.amount(), AUTHORIZATION_TTL);
        authorizationRecordRepository.save(record);

        tx.authorize();
        tx.capture();
        Ledger ledger = ledgerRepository.findFirst();
        FundsHold hold = holdRepository.findActiveByTransactionId(transactionId).orElseThrow();
        ledger.capture(tx, hold, wallet, floatAccount);
        holdRepository.save(hold);
        accountRepository.save(wallet);
        record.consume();
        authorizationRecordRepository.save(record);

        tx.pendSettlement();
        tx.settle();
        tx.markCompleted();

        flushHistorySince(tx);
        transactionRepository.save(tx);
        return CashOutResult.of(tx);
    }

    private CashOutResult recordAuthorizationUnknown(Id transactionId) {
        Transaction tx = reload(transactionId);
        tx.markUnknown();
        flushHistorySince(tx);
        transactionRepository.save(tx);
        return CashOutResult.of(tx);
    }

    private CashOutResult recordCaptureUnknown(Id transactionId, AuthorizationResult authorization, CashOutCommand cmd) {
        Transaction tx = reload(transactionId);
        authorizationRecordRepository.save(AuthorizationRecord.create(
                transactionId, authorization.providerReference(), cmd.amount(), AUTHORIZATION_TTL));
        tx.authorize();
        tx.markUnknown();
        flushHistorySince(tx);
        transactionRepository.save(tx);
        return CashOutResult.of(tx);
    }

    private CashOutResult failAuthorization(Id transactionId) {
        Transaction tx = reload(transactionId);
        tx.failAuthorization();
        releaseHold(tx);
        flushHistorySince(tx);
        transactionRepository.save(tx);
        return CashOutResult.of(tx);
    }

    private CashOutResult failCapture(Id transactionId, AuthorizationResult authorization, CashOutCommand cmd) {
        Transaction tx = reload(transactionId);

        AuthorizationRecord record = AuthorizationRecord.create(
                transactionId, authorization.providerReference(), cmd.amount(), AUTHORIZATION_TTL);
        authorizationRecordRepository.save(record);
        tx.authorize();
        record.cancel();
        authorizationRecordRepository.save(record);
        tx.failCapture();
        releaseHold(tx);

        flushHistorySince(tx);
        transactionRepository.save(tx);
        return CashOutResult.of(tx);
    }

    private void releaseHold(Transaction tx) {
        holdRepository.findActiveByTransactionId(tx.snapshot().transactionId()).ifPresent(hold -> {
            Account wallet = accountRepository.findByIdForUpdate(tx.snapshot().fromAccountId())
                    .orElseThrow();
            ledgerRepository.findFirst().release(hold, wallet);
            holdRepository.save(hold);
        });
    }

    private Transaction reload(Id transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow();
    }

    private Account loadFloatAccount() {
        // No lock: the float balance is never written (ADR-001), so locking it would
        // serialize every cash-in and cash-out for nothing.
        return accountRepository.findFloatByProviderId(SystemConstants.PROVIDER_ID)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Float account not found for provider: " + SystemConstants.PROVIDER_ID.value()));
    }

    /**
     * Index 0 was persisted by TX-1; everything after it happened in memory since.
     */
    private void flushHistorySince(Transaction tx) {
        tx.history().subList(1, tx.history().size()).forEach(trxHistoricStatesRepository::save);
    }

    private record Initiated(Id transactionId, PhoneNumber phone) {
    }
}
