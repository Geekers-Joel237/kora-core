package com.geekersjoel237.koracore.application;

import com.geekersjoel237.koracore.application.command.CashInCommand;
import com.geekersjoel237.koracore.application.command.CashOutCommand;
import com.geekersjoel237.koracore.application.command.ReversePaymentCommand;
import com.geekersjoel237.koracore.application.command.TransferCommand;
import com.geekersjoel237.koracore.application.service.AuthService;
import com.geekersjoel237.koracore.application.service.PaymentService;
import com.geekersjoel237.koracore.application.service.PaymentTransactionalExecutor;
import com.geekersjoel237.koracore.domain.enums.OperationType;
import com.geekersjoel237.koracore.domain.enums.ProviderOperationType;
import com.geekersjoel237.koracore.domain.enums.Role;
import com.geekersjoel237.koracore.domain.enums.UserStatus;
import com.geekersjoel237.koracore.domain.exception.*;
import com.geekersjoel237.koracore.domain.model.*;
import com.geekersjoel237.koracore.domain.port.CustomerPinEncoder;
import com.geekersjoel237.koracore.domain.port.LedgerRepository;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.infrastructure.config.SecurityProperties;
import com.geekersjoel237.koracore.infrastructure.security.BCryptCustomerPinEncoder;
import com.geekersjoel237.koracore.shared.NoopTransactionManager;
import com.geekersjoel237.koracore.shared.inmemory.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

import static com.geekersjoel237.koracore.domain.model.state.TransactionState.AUTHORIZATION_FAILED;
import static com.geekersjoel237.koracore.domain.model.state.TransactionState.COMPLETED;
import static com.geekersjoel237.koracore.domain.model.state.TransactionState.REVERSED;
import static com.geekersjoel237.koracore.shared.inmemory.InMemoryProviderAdapter.Behavior.FAIL;
import static com.geekersjoel237.koracore.shared.inmemory.InMemoryProviderAdapter.Behavior.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentUseCaseTest {

    private static final Id CUST_ID_A = new Id("cust-001");
    private static final Id CUST_ID_B = new Id("cust-002");
    private static final Id PROVIDER_ID = new Id("provider-system-001");
    private static final String EMAIL_A = "a@koracore.com";
    private static final String EMAIL_B = "b@koracore.com";
    private static final String RAW_PIN = "123456";
    private static final String PAYMENT_METHOD = "MOBILE_MONEY";
    private static final Amount AMOUNT_10K = Amount.of(BigDecimal.valueOf(10_000), "XOF");
    private static final Amount AMOUNT_5K = Amount.of(BigDecimal.valueOf(5_000), "XOF");
    private static final Amount AMOUNT_ZERO = Amount.of(BigDecimal.ZERO, "XOF");

    private static final SecurityProperties TEST_SECURITY = new SecurityProperties(
            new SecurityProperties.Jwt("test-secret-key-must-be-at-least-32-chars!!", 15, 7),
            new SecurityProperties.Otp(5)
    );

    private final CustomerPinEncoder pinEncoder = new BCryptCustomerPinEncoder(4);

    private InMemoryAccountRepository accountRepo;
    private InMemoryCustomerRepository customerRepo;
    private InMemoryTransactionRepository transactionRepo;
    private InMemoryTrxHistoricStatesRepository historicRepo;
    private InMemoryAuthorizationRecordRepository authorizationRecordRepo;
    private InMemoryOtpStore otpStore;
    private InMemoryProviderAdapter provider;
    private InMemoryMailPort mailPort;
    private AuthService authService;
    private PaymentService paymentService;
    private LedgerRepository ledgerRepository;

    private static PhoneNumber phoneNumberA() {
        return PhoneNumber.of("+237", "600000001");
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static PhoneNumber phoneNumberB() {
        return PhoneNumber.of("+237", "600000002");
    }

    @BeforeEach
    void setUp() {
        accountRepo = new InMemoryAccountRepository();
        customerRepo = new InMemoryCustomerRepository();
        transactionRepo = new InMemoryTransactionRepository();
        historicRepo = new InMemoryTrxHistoricStatesRepository();
        otpStore = new InMemoryOtpStore(Clock.systemUTC());
        provider = new InMemoryProviderAdapter(SUCCESS);
        provider.reset();
        ledgerRepository = new InMemoryLedgerRepository(Ledger.create(Id.generate()));
        mailPort = new InMemoryMailPort();
        authorizationRecordRepo = new InMemoryAuthorizationRecordRepository();
        authService = new AuthService(
                new InMemoryUserRepository(), customerRepo, accountRepo, otpStore, pinEncoder, Clock.systemUTC(), TEST_SECURITY, mailPort);
        PaymentTransactionalExecutor executor = new PaymentTransactionalExecutor(
                new NoopTransactionManager(),
                authService, accountRepo, customerRepo,
                transactionRepo, historicRepo, provider, ledgerRepository,
                authorizationRecordRepo);
        paymentService = new PaymentService(executor, accountRepo);

        preloadCustomerA();
        preloadFloatAccount();
    }

    private void preloadCustomerA() {
        User userA = User.create(CUST_ID_A, "Customer A", EMAIL_A, Role.CUSTOMER);
        customerRepo.save(Customer.create(userA, phoneNumberA(), RAW_PIN, pinEncoder));
        accountRepo.save(Account.createCustomerAccount(Id.generate(), CUST_ID_A));
    }

    private void preloadCustomerB() {
        User userB = User.create(CUST_ID_B, "Customer B", EMAIL_B, Role.CUSTOMER);
        customerRepo.save(Customer.create(userB, phoneNumberB(), RAW_PIN, pinEncoder));
        accountRepo.save(Account.createCustomerAccount(Id.generate(), CUST_ID_B));
    }

    private void preloadFloatAccount() {
        accountRepo.save(Account.createFloatAccount(Id.generate(), PROVIDER_ID));
    }

    private void suspendCustomerA() {
        replaceWithStatus(CUST_ID_A, UserStatus.SUSPENDED);
    }

    private void suspendCustomerB() {
        replaceWithStatus(CUST_ID_B, UserStatus.SUSPENDED);
    }

    private void replaceWithStatus(Id customerId, UserStatus status) {
        Customer c = customerRepo.findById(customerId).orElseThrow();
        Customer.Snapshot snap = c.snapshot();
        User.Snapshot oldUser = snap.user();
        User.Snapshot newUser = new User.Snapshot(
                oldUser.id(), oldUser.fullName(), oldUser.email(), oldUser.role(), status);
        customerRepo.save(Customer.createFromSnapshot(
                new Customer.Snapshot(snap.customerId(), newUser, snap.phoneNumber(), snap.hashedPin())));
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

        assertTrue(totalDebit.equals(totalCredit),
                "Double-entry violated: DEBIT=" + totalDebit.value()
                        + " CREDIT=" + totalCredit.value());
    }

    // ── Groupe 1 — cashIn ─────────────────────────────────────────────────────

    @Test
    void should_complete_cash_in_when_provider_succeeds() {
        CashInCommand cmd = new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD);
        Transaction tx = paymentService.cashIn(cmd);

        assertEquals(COMPLETED, tx.snapshot().state());
        assertEquals(2, tx.snapshot().operations().size());
        assertEquals(1, transactionRepo.count());

        List<TrxStateHistoric> history =
                historicRepo.findByTransactionId(tx.snapshot().transactionId());
        // null→INITIALIZED, INITIALIZED→AUTHORIZED, AUTHORIZED→CAPTURED,
        // CAPTURED→SETTLEMENT_PENDING, SETTLEMENT_PENDING→SETTLED, SETTLED→COMPLETED
        assertEquals(6, history.size());
        assertNull(                                history.get(0).snapshot().oldState());
        assertEquals("INITIALIZED",               history.get(0).snapshot().newState());
        assertEquals("INITIALIZED",               history.get(1).snapshot().oldState());
        assertEquals("AUTHORIZED",                history.get(1).snapshot().newState());
        assertEquals("AUTHORIZED",                history.get(2).snapshot().oldState());
        assertEquals("CAPTURED",                  history.get(2).snapshot().newState());
        assertEquals("CAPTURED",                  history.get(3).snapshot().oldState());
        assertEquals("SETTLEMENT_PENDING",        history.get(3).snapshot().newState());
        assertEquals("SETTLEMENT_PENDING",        history.get(4).snapshot().oldState());
        assertEquals("SETTLED",                   history.get(4).snapshot().newState());
        assertEquals("SETTLED",                   history.get(5).snapshot().oldState());
        assertEquals("COMPLETED",                 history.get(5).snapshot().newState());

        Account customerAccount = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
        assertTrue(AMOUNT_10K.equals(customerAccount.snapshot().balance().solde()));

        assertThat(provider.getLastOperationType()).isEqualTo(ProviderOperationType.COLLECTION);
        assertThat(provider.getLastCustomerPhone()).isEqualTo(phoneNumberA());
    }

    @Test
    void should_fail_cash_in_and_keep_balance_unchanged_when_provider_fails() {
        provider.setBehavior(FAIL);
        CashInCommand cmd = new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD);
        Transaction tx = paymentService.cashIn(cmd);

        assertEquals(AUTHORIZATION_FAILED, tx.snapshot().state());
        assertEquals(0, tx.snapshot().operations().size());

        List<TrxStateHistoric> history =
                historicRepo.findByTransactionId(tx.snapshot().transactionId());
        // null→INITIALIZED, INITIALIZED→AUTHORIZATION_FAILED
        assertEquals(2, history.size());
        assertNull(history.get(0).snapshot().oldState());
        assertEquals("INITIALIZED",          history.get(0).snapshot().newState());
        assertEquals("INITIALIZED",          history.get(1).snapshot().oldState());
        assertEquals("AUTHORIZATION_FAILED", history.get(1).snapshot().newState());

        Account customerAccount = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
        assertTrue(AMOUNT_ZERO.equals(customerAccount.snapshot().balance().solde()));

        assertDoubleEntryInvariant();
    }

    @Test
    void should_throw_pin_validation_exception_and_persist_nothing_when_pin_is_wrong() {
        CashInCommand cmd = new CashInCommand(CUST_ID_A, "wrong", AMOUNT_10K, PAYMENT_METHOD);
        assertThatThrownBy(() -> paymentService.cashIn(cmd))
                .isInstanceOf(PinValidationException.class);
        assertEquals(0, transactionRepo.count());
    }

    @Test
    void should_throw_customer_not_found_exception_and_persist_nothing_when_account_missing() {
        CashInCommand cmd = new CashInCommand(new Id("ghost"), RAW_PIN, AMOUNT_10K, PAYMENT_METHOD);
        assertThatThrownBy(() -> paymentService.cashIn(cmd))
                .isInstanceOf(CustomerNotFoundException.class);
        assertEquals(0, transactionRepo.count());
    }

    @Test
    void should_throw_account_suspended_exception_and_persist_nothing_when_customer_suspended() {
        suspendCustomerA();
        CashInCommand cmd = new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD);
        assertThatThrownBy(() -> paymentService.cashIn(cmd))
                .isInstanceOf(AccountSuspendedException.class);
        assertEquals(0, transactionRepo.count());
    }

    @Test
    void should_throw_illegal_argument_exception_and_persist_nothing_when_amount_is_zero() {
        CashInCommand cmd = new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_ZERO, PAYMENT_METHOD);
        assertThatThrownBy(() -> paymentService.cashIn(cmd))
                .isInstanceOf(IllegalArgumentException.class);
        assertEquals(0, transactionRepo.count());
    }

    // ── Groupe 2 — cashOut ────────────────────────────────────────────────────

    @Test
    void should_complete_cash_out_when_provider_succeeds() {
        paymentService.cashIn(new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));

        Transaction tx = paymentService.cashOut(
                new CashOutCommand(CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD));

        assertEquals(COMPLETED, tx.snapshot().state());
        Account account = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
        assertTrue(AMOUNT_5K.equals(account.snapshot().balance().solde()));

        assertThat(provider.getLastOperationType()).isEqualTo(ProviderOperationType.DISBURSEMENT);
    }

    @Test
    void should_fail_cash_out_and_restore_balance_when_provider_fails() {
        paymentService.cashIn(new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
        provider.setBehavior(FAIL);

        Transaction tx = paymentService.cashOut(
                new CashOutCommand(CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD));

        assertEquals(AUTHORIZATION_FAILED, tx.snapshot().state());
        Account account = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
        assertTrue(AMOUNT_10K.equals(account.snapshot().balance().solde()));
        assertDoubleEntryInvariant();
    }

    @Test
    void should_throw_insufficient_funds_exception_and_persist_nothing_when_balance_too_low() {
        assertThatThrownBy(() -> paymentService.cashOut(
                new CashOutCommand(CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD)))
                .isInstanceOf(InsufficientFundsException.class);
        assertEquals(0, transactionRepo.count());
    }

    @Test
    void should_throw_pin_validation_exception_and_persist_nothing_on_cash_out_wrong_pin() {
        paymentService.cashIn(new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
        assertThatThrownBy(() -> paymentService.cashOut(
                new CashOutCommand(CUST_ID_A, "wrong", AMOUNT_5K, PAYMENT_METHOD)))
                .isInstanceOf(PinValidationException.class);
        assertEquals(1, transactionRepo.count());
    }

    // ── Groupe 3 — transfer ───────────────────────────────────────────────────

    @Test
    void should_complete_transfer_and_update_both_balances_when_provider_succeeds() {
        preloadCustomerB();
        paymentService.cashIn(new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
        provider.reset(); // isolate transfer — verify it makes no provider call

        Transaction tx = paymentService.transfer(new TransferCommand(
                CUST_ID_A, RAW_PIN, AMOUNT_5K, phoneNumberB().fullNumber()));

        assertEquals(COMPLETED, tx.snapshot().state());

        Account accountA = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
        Account accountB = accountRepo.findByCustomerId(CUST_ID_B).orElseThrow();
        assertTrue(AMOUNT_5K.equals(accountA.snapshot().balance().solde()));
        assertTrue(AMOUNT_5K.equals(accountB.snapshot().balance().solde()));

        Amount sumAB = accountA.snapshot().balance().solde()
                .add(accountB.snapshot().balance().solde());
        assertTrue(AMOUNT_10K.equals(sumAB));

        assertThat(provider.getLastOperationType()).isNull();
    }

    @Test
    void should_restore_balances_when_transfer_fails_due_to_insufficient_funds() {
        preloadCustomerB();
        paymentService.cashIn(new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD));

        assertThatThrownBy(() -> paymentService.transfer(new TransferCommand(
                CUST_ID_A, RAW_PIN, AMOUNT_10K, phoneNumberB().fullNumber())))
                .isInstanceOf(InsufficientFundsException.class);

        assertEquals(1, transactionRepo.count()); // only the cashIn
        Account accountA = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
        Account accountB = accountRepo.findByCustomerId(CUST_ID_B).orElseThrow();
        assertTrue(AMOUNT_5K.equals(accountA.snapshot().balance().solde()));
        assertTrue(AMOUNT_ZERO.equals(accountB.snapshot().balance().solde()));
        assertDoubleEntryInvariant();
    }

    @Test
    void should_complete_transfer_and_record_correct_history() {
        preloadCustomerB();
        paymentService.cashIn(new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));

        Transaction tx = paymentService.transfer(new TransferCommand(
                CUST_ID_A, RAW_PIN, AMOUNT_5K, phoneNumberB().fullNumber()));

        assertEquals(COMPLETED, tx.snapshot().state());

        List<TrxStateHistoric> history =
                historicRepo.findByTransactionId(tx.snapshot().transactionId());
        assertEquals(6, history.size());
        assertNull(                         history.get(0).snapshot().oldState());
        assertEquals("INITIALIZED",         history.get(0).snapshot().newState());
        assertEquals("INITIALIZED",         history.get(1).snapshot().oldState());
        assertEquals("AUTHORIZED",          history.get(1).snapshot().newState());
        assertEquals("AUTHORIZED",          history.get(2).snapshot().oldState());
        assertEquals("CAPTURED",            history.get(2).snapshot().newState());
        assertEquals("CAPTURED",            history.get(3).snapshot().oldState());
        assertEquals("SETTLEMENT_PENDING",  history.get(3).snapshot().newState());
        assertEquals("SETTLEMENT_PENDING",  history.get(4).snapshot().oldState());
        assertEquals("SETTLED",             history.get(4).snapshot().newState());
        assertEquals("SETTLED",             history.get(5).snapshot().oldState());
        assertEquals("COMPLETED",           history.get(5).snapshot().newState());

        assertTrue(authorizationRecordRepo
                .findActiveByTransactionId(tx.snapshot().transactionId()).isEmpty());
    }

    @Test
    void should_throw_account_not_found_when_recipient_phone_unknown() {
        assertThatThrownBy(() -> paymentService.transfer(new TransferCommand(
                CUST_ID_A, RAW_PIN, AMOUNT_5K, "+2250000000000")))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void should_throw_account_suspended_when_recipient_is_suspended() {
        preloadCustomerB();
        suspendCustomerB();
        assertThatThrownBy(() -> paymentService.transfer(new TransferCommand(
                CUST_ID_A, RAW_PIN, AMOUNT_5K, phoneNumberB().fullNumber())))
                .isInstanceOf(AccountSuspendedException.class);
    }

    @Test
    void should_throw_self_transfer_exception_when_sender_equals_recipient() {
        assertThatThrownBy(() -> paymentService.transfer(new TransferCommand(
                CUST_ID_A, RAW_PIN, AMOUNT_5K, phoneNumberA().fullNumber())))
                .isInstanceOf(SelfTransferException.class);
    }

    @Test
    void should_throw_insufficient_funds_exception_when_balance_too_low_for_transfer() {
        preloadCustomerB();
        assertThatThrownBy(() -> paymentService.transfer(new TransferCommand(
                CUST_ID_A, RAW_PIN, AMOUNT_5K, phoneNumberB().fullNumber())))
                .isInstanceOf(InsufficientFundsException.class);
    }

    // ── Groupe 4 — getBalance ─────────────────────────────────────────────────

    @Test
    void should_return_zero_balance_for_new_customer_account() {
        Account account = paymentService.getBalance(CUST_ID_A);

        assertTrue(AMOUNT_ZERO.equals(account.snapshot().balance().solde()));
    }

    @Test
    void should_return_balance_reflecting_cash_in() {
        paymentService.cashIn(new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));

        Account account = paymentService.getBalance(CUST_ID_A);

        assertTrue(AMOUNT_10K.equals(account.snapshot().balance().solde()));
    }

    @Test
    void should_return_balance_reflecting_cash_in_then_cash_out() {
        paymentService.cashIn(new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
        paymentService.cashOut(new CashOutCommand(CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD));

        Account account = paymentService.getBalance(CUST_ID_A);

        assertTrue(AMOUNT_5K.equals(account.snapshot().balance().solde()));
    }

    @Test
    void should_throw_account_not_found_when_customer_has_no_account() {
        assertThatThrownBy(() -> paymentService.getBalance(new Id("ghost-customer")))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ── Groupe 5 — reversal ───────────────────────────────────────────────────

    @Nested
    class ReversalTests {

        @Test
        void should_debit_customer_and_restore_zero_balance_when_cash_in_reversed() {
            Transaction tx = paymentService.cashIn(
                    new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));

            Account before = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
            assertTrue(AMOUNT_10K.equals(before.snapshot().balance().solde()));

            paymentService.reversePayment(new ReversePaymentCommand(
                    tx.snapshot().transactionId(), "op-001", "OPERATOR", "chargeback", "corr-001"));

            Account after = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
            assertTrue(AMOUNT_ZERO.equals(after.snapshot().balance().solde()));
        }

        @Test
        void should_credit_customer_and_restore_original_balance_when_cash_out_reversed() {
            paymentService.cashIn(new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
            Transaction cashOut = paymentService.cashOut(
                    new CashOutCommand(CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD));

            Account midway = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
            assertTrue(AMOUNT_5K.equals(midway.snapshot().balance().solde()));

            paymentService.reversePayment(new ReversePaymentCommand(
                    cashOut.snapshot().transactionId(), "op-001", "OPERATOR", "dispute", "corr-002"));

            Account after = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
            assertTrue(AMOUNT_10K.equals(after.snapshot().balance().solde()));
        }

        @Test
        void should_credit_sender_and_debit_receiver_when_p2p_reversed() {
            preloadCustomerB();
            paymentService.cashIn(new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
            Transaction transfer = paymentService.transfer(new TransferCommand(
                    CUST_ID_A, RAW_PIN, AMOUNT_5K, phoneNumberB().fullNumber()));

            paymentService.reversePayment(new ReversePaymentCommand(
                    transfer.snapshot().transactionId(), "op-001", "OPERATOR", "fraud", "corr-003"));

            Account accountA = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
            Account accountB = accountRepo.findByCustomerId(CUST_ID_B).orElseThrow();
            assertTrue(AMOUNT_10K.equals(accountA.snapshot().balance().solde()),
                    "Sender should be back to 10K after reversal");
            assertTrue(AMOUNT_ZERO.equals(accountB.snapshot().balance().solde()),
                    "Receiver should be back to 0 after reversal");
        }

        @Test
        void double_entry_invariant_holds_across_cash_in_and_reversal() {
            Transaction tx = paymentService.cashIn(
                    new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));

            paymentService.reversePayment(new ReversePaymentCommand(
                    tx.snapshot().transactionId(), "op-001", "OPERATOR", "test", "corr-004"));

            assertDoubleEntryInvariant();
        }

        @Test
        void should_throw_invalid_state_transition_when_reversing_already_reversed_transaction() {
            Transaction tx = paymentService.cashIn(
                    new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));

            paymentService.reversePayment(new ReversePaymentCommand(
                    tx.snapshot().transactionId(), "op-001", "OPERATOR", "first", "corr-005"));

            Transaction reversed = transactionRepo.findById(tx.snapshot().transactionId()).orElseThrow();
            assertEquals(REVERSED, reversed.snapshot().state());

            assertThatThrownBy(() -> paymentService.reversePayment(new ReversePaymentCommand(
                    tx.snapshot().transactionId(), "op-001", "OPERATOR", "second", "corr-006")))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }
}