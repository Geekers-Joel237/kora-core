package com.geekersjoel237.koracore.application;

import com.geekersjoel237.koracore.application.command.AuthorizePaymentCommand;
import com.geekersjoel237.koracore.application.command.CapturePaymentCommand;
import com.geekersjoel237.koracore.application.command.CashInCommand;
import com.geekersjoel237.koracore.application.command.ReversePaymentCommand;
import com.geekersjoel237.koracore.application.service.AuthService;
import com.geekersjoel237.koracore.application.service.PaymentService;
import com.geekersjoel237.koracore.application.service.PaymentTransactionalExecutor;
import com.geekersjoel237.koracore.domain.enums.OperationType;
import com.geekersjoel237.koracore.domain.enums.Role;
import com.geekersjoel237.koracore.domain.exception.InsufficientFundsException;
import com.geekersjoel237.koracore.domain.exception.InvalidStateTransitionException;
import com.geekersjoel237.koracore.domain.exception.PinValidationException;
import com.geekersjoel237.koracore.domain.model.Account;
import com.geekersjoel237.koracore.domain.model.AuthorizationRecord;
import com.geekersjoel237.koracore.domain.model.Customer;
import com.geekersjoel237.koracore.domain.model.Ledger;
import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.model.User;
import com.geekersjoel237.koracore.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.domain.port.CustomerPinEncoder;
import com.geekersjoel237.koracore.domain.port.LedgerRepository;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.infrastructure.security.BCryptCustomerPinEncoder;
import com.geekersjoel237.koracore.shared.inmemory.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.geekersjoel237.koracore.shared.inmemory.InMemoryProviderSimulator.Behavior.FAIL;
import static com.geekersjoel237.koracore.shared.inmemory.InMemoryProviderSimulator.Behavior.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentLifecycleUseCaseTest {

    private static final Id CUST_ID_A    = new Id("cust-lifecycle-001");
    private static final Id PROVIDER_ID  = new Id("provider-system-001");
    private static final String EMAIL_A       = "lifecycle-a@koracore.com";
    private static final String RAW_PIN       = "123456";
    private static final String PAYMENT_METHOD = "MOBILE_MONEY";
    private static final String CORRELATION_ID = "corr-test-001";
    private static final Amount AMOUNT_5K  = Amount.of(BigDecimal.valueOf(5_000), "XOF");
    private static final Amount AMOUNT_ZERO = Amount.of(BigDecimal.ZERO, "XOF");

    private final CustomerPinEncoder pinEncoder = new BCryptCustomerPinEncoder();

    private InMemoryAccountRepository           accountRepo;
    private InMemoryCustomerRepository          customerRepo;
    private InMemoryTransactionRepository       transactionRepo;
    private InMemoryTrxHistoricStatesRepository historicRepo;
    private InMemoryAuthorizationRecordRepository authRecordRepo;
    private InMemoryProviderSimulator           provider;
    private AuthService                         authService;
    private PaymentService                      paymentService;

    // ── setup ─────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        accountRepo     = new InMemoryAccountRepository();
        customerRepo    = new InMemoryCustomerRepository();
        transactionRepo = new InMemoryTransactionRepository();
        historicRepo    = new InMemoryTrxHistoricStatesRepository();
        authRecordRepo  = new InMemoryAuthorizationRecordRepository();
        provider        = new InMemoryProviderSimulator(SUCCESS);

        LedgerRepository ledgerRepository = new InMemoryLedgerRepository(Ledger.create(Id.generate()));
        InMemoryOtpStore otpStore = new InMemoryOtpStore(Clock.systemUTC());
        InMemoryMailPort mailPort = new InMemoryMailPort();

        authService = new AuthService(
                new InMemoryUserRepository(), customerRepo, accountRepo, otpStore, pinEncoder,
                Clock.systemUTC(), mailPort);

        PaymentTransactionalExecutor executor = new PaymentTransactionalExecutor(
                authService, accountRepo, customerRepo,
                transactionRepo, historicRepo, provider, ledgerRepository,
                authRecordRepo);
        paymentService = new PaymentService(executor, accountRepo);

        preloadCustomerA();
        preloadFloatAccount();
    }

    private void preloadCustomerA() {
        User userA = User.create(CUST_ID_A, "Customer A", EMAIL_A, Role.CUSTOMER);
        customerRepo.save(Customer.create(userA, PhoneNumber.of("+237", "600000011"), RAW_PIN, pinEncoder));
        accountRepo.save(Account.createCustomerAccount(Id.generate(), CUST_ID_A));
    }

    private void preloadFloatAccount() {
        accountRepo.save(Account.createFloatAccount(Id.generate(), PROVIDER_ID));
    }

    private void giveFunds(Amount amount) {
        paymentService.cashIn(new CashInCommand(CUST_ID_A, RAW_PIN, amount, PAYMENT_METHOD));
    }

    private Transaction doAuthorize() {
        return paymentService.authorizePayment(new AuthorizePaymentCommand(
                CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD, CORRELATION_ID));
    }

    private Transaction doCapture(Id txId) {
        return paymentService.capturePayment(new CapturePaymentCommand(
                txId, CUST_ID_A, CORRELATION_ID));
    }

    private Transaction doReverse(Id txId) {
        return paymentService.reversePayment(new ReversePaymentCommand(
                txId, "admin-001", "ADMIN", "Customer request", CORRELATION_ID));
    }

    private void assertDoubleEntryInvariant() {
        Amount totalDebit = transactionRepo.findAll().stream()
                .flatMap(tx -> tx.operations().stream())
                .filter(op -> op.snapshot().type() == OperationType.DEBIT)
                .map(op -> op.snapshot().amount())
                .reduce(AMOUNT_ZERO, Amount::add);

        Amount totalCredit = transactionRepo.findAll().stream()
                .flatMap(tx -> tx.operations().stream())
                .filter(op -> op.snapshot().type() == OperationType.CREDIT)
                .map(op -> op.snapshot().amount())
                .reduce(AMOUNT_ZERO, Amount::add);

        assertThat(totalDebit.equals(totalCredit))
                .as("Double-entry violated: DEBIT=%s CREDIT=%s", totalDebit.value(), totalCredit.value())
                .isTrue();
    }

    // ── authorizePayment ──────────────────────────────────────────────────────

    @Test
    void should_create_authorization_record_on_successful_authorize() {
        giveFunds(AMOUNT_5K);

        Transaction tx = doAuthorize();

        AuthorizationRecord authRecord = authRecordRepo
                .findByTransactionId(tx.snapshot().transactionId()).orElseThrow();
        assertThat(authRecord.isActive()).isTrue();
        assertThat(authRecord.snapshot().status())
                .isEqualTo(AuthorizationRecord.AuthorizationStatus.ACTIVE);
        assertThat(authRecord.snapshot().authorizedAmount()).isEqualTo(AMOUNT_5K);
    }

    @Test
    void should_transition_to_authorized_on_success() {
        giveFunds(AMOUNT_5K);

        Transaction tx = doAuthorize();

        assertThat(tx.snapshot().state()).isEqualTo(TransactionState.AUTHORIZED);
    }

    @Test
    void should_transition_to_authorization_failed_on_provider_failure() {
        giveFunds(AMOUNT_5K);
        provider.setBehavior(FAIL);

        Transaction tx = doAuthorize();

        assertThat(tx.snapshot().state()).isEqualTo(TransactionState.AUTHORIZATION_FAILED);
    }

    @Test
    void should_throw_insufficient_funds_when_balance_too_low() {
        // Customer has 0 balance — authorize with any positive amount should fail
        assertThatThrownBy(this::doAuthorize)
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void should_throw_pin_validation_exception_when_pin_is_wrong() {
        giveFunds(AMOUNT_5K);

        assertThatThrownBy(() -> paymentService.authorizePayment(new AuthorizePaymentCommand(
                CUST_ID_A, "wrong-pin", AMOUNT_5K, PAYMENT_METHOD, CORRELATION_ID)))
                .isInstanceOf(PinValidationException.class);
    }

    // ── capturePayment ────────────────────────────────────────────────────────

    @Test
    void should_write_two_ledger_entries_on_successful_capture() {
        giveFunds(AMOUNT_5K);
        Transaction authTx = doAuthorize();

        Transaction captureTx = doCapture(authTx.snapshot().transactionId());

        assertThat(captureTx.snapshot().operations()).hasSize(2);
    }

    @Test
    void should_transition_to_settlement_pending_on_successful_capture() {
        giveFunds(AMOUNT_5K);
        Transaction authTx = doAuthorize();

        Transaction captureTx = doCapture(authTx.snapshot().transactionId());

        assertThat(captureTx.snapshot().state()).isEqualTo(TransactionState.SETTLEMENT_PENDING);
    }

    @Test
    void should_debit_customer_balance_on_successful_capture() {
        giveFunds(AMOUNT_5K);
        Transaction authTx = doAuthorize();

        doCapture(authTx.snapshot().transactionId());

        Account account = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
        assertThat(account.snapshot().balance().solde().value())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_consume_authorization_record_on_capture() {
        giveFunds(AMOUNT_5K);
        Transaction authTx = doAuthorize();

        doCapture(authTx.snapshot().transactionId());

        AuthorizationRecord authRecord = authRecordRepo
                .findByTransactionId(authTx.snapshot().transactionId()).orElseThrow();
        assertThat(authRecord.snapshot().status())
                .isEqualTo(AuthorizationRecord.AuthorizationStatus.CONSUMED);
    }

    @Test
    void should_double_entry_invariant_holds_after_capture() {
        giveFunds(AMOUNT_5K);
        Transaction authTx = doAuthorize();
        doCapture(authTx.snapshot().transactionId());

        assertDoubleEntryInvariant();
    }

    @Test
    void should_transition_to_authorization_failed_when_ttl_expired() {
        giveFunds(AMOUNT_5K);
        Transaction authTx = doAuthorize();
        Id txId = authTx.snapshot().transactionId();

        // Replace the auth record with an expired version (status still ACTIVE but TTL past)
        AuthorizationRecord original = authRecordRepo.findByTransactionId(txId).orElseThrow();
        AuthorizationRecord.Snapshot expiredSnap = new AuthorizationRecord.Snapshot(
                original.snapshot().id(),
                original.snapshot().transactionId(),
                original.snapshot().providerReference(),
                original.snapshot().authorizedAmount(),
                original.snapshot().authorizedAt(),
                Instant.now().minus(1, ChronoUnit.MINUTES),          // already expired
                AuthorizationRecord.AuthorizationStatus.ACTIVE);     // status still ACTIVE
        authRecordRepo.save(AuthorizationRecord.createFromSnapshot(expiredSnap));

        Transaction captureTx = doCapture(txId);

        assertThat(captureTx.snapshot().state()).isEqualTo(TransactionState.AUTHORIZATION_FAILED);
    }

    @Test
    void should_transition_to_capture_failed_on_provider_failure() {
        giveFunds(AMOUNT_5K);
        Transaction authTx = doAuthorize();
        provider.setBehavior(FAIL);

        Transaction captureTx = doCapture(authTx.snapshot().transactionId());

        assertThat(captureTx.snapshot().state()).isEqualTo(TransactionState.CAPTURE_FAILED);
    }

    @Test
    void should_cancel_authorization_record_on_capture_failure() {
        giveFunds(AMOUNT_5K);
        Transaction authTx = doAuthorize();
        provider.setBehavior(FAIL);

        doCapture(authTx.snapshot().transactionId());

        AuthorizationRecord authRecord = authRecordRepo
                .findByTransactionId(authTx.snapshot().transactionId()).orElseThrow();
        assertThat(authRecord.snapshot().status())
                .isEqualTo(AuthorizationRecord.AuthorizationStatus.CANCELLED);
    }

    // ── reversePayment ────────────────────────────────────────────────────────

    @Test
    void should_reverse_pre_capture_with_no_ledger_entries() {
        giveFunds(AMOUNT_5K);
        Transaction authTx = doAuthorize();

        Transaction reversed = doReverse(authTx.snapshot().transactionId());

        assertThat(reversed.snapshot().state()).isEqualTo(TransactionState.REVERSED);
        assertThat(reversed.snapshot().operations()).isEmpty();
    }

    @Test
    void should_reverse_post_capture_with_four_total_ledger_entries() {
        giveFunds(AMOUNT_5K);
        Transaction authTx = doAuthorize();
        Transaction captureTx = doCapture(authTx.snapshot().transactionId());

        // Transaction is now SETTLEMENT_PENDING — Branch B applies
        Transaction reversed = doReverse(captureTx.snapshot().transactionId());

        assertThat(reversed.snapshot().state()).isEqualTo(TransactionState.REVERSED);
        assertThat(reversed.snapshot().operations()).hasSize(4);
    }

    @Test
    void should_restore_customer_balance_on_post_capture_reversal() {
        giveFunds(AMOUNT_5K);
        Transaction authTx = doAuthorize();
        doCapture(authTx.snapshot().transactionId());

        // After capture: customer balance = 0
        doReverse(authTx.snapshot().transactionId());

        // After reversal: balance should be restored to 5K
        Account account = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
        assertThat(account.snapshot().balance().solde().value())
                .isEqualByComparingTo(BigDecimal.valueOf(5_000));
    }

    @Test
    void should_net_ledger_zero_after_post_capture_reversal() {
        giveFunds(AMOUNT_5K);
        Transaction authTx = doAuthorize();
        doCapture(authTx.snapshot().transactionId());
        doReverse(authTx.snapshot().transactionId());

        // Sum of all credits == sum of all debits across ALL transactions
        assertDoubleEntryInvariant();
    }

    @Test
    void should_throw_illegal_argument_when_reason_is_null() {
        giveFunds(AMOUNT_5K);
        Transaction authTx = doAuthorize();

        assertThatThrownBy(() -> paymentService.reversePayment(new ReversePaymentCommand(
                authTx.snapshot().transactionId(), "admin-001", "ADMIN", null, CORRELATION_ID)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_illegal_argument_when_reason_is_blank() {
        giveFunds(AMOUNT_5K);
        Transaction authTx = doAuthorize();

        assertThatThrownBy(() -> paymentService.reversePayment(new ReversePaymentCommand(
                authTx.snapshot().transactionId(), "admin-001", "ADMIN", "   ", CORRELATION_ID)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_invalid_state_transition_from_completed() {
        // cashIn goes all the way to COMPLETED — cannot reverse a COMPLETED tx
        giveFunds(AMOUNT_5K);
        Transaction completedTx = paymentService.cashIn(
                new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD));

        assertThatThrownBy(() -> doReverse(completedTx.snapshot().transactionId()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }
}