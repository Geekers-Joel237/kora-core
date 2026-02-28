package com.geekersjoel237.koracore.application;

import com.geekersjoel237.koracore.application.command.CashInCommand;
import com.geekersjoel237.koracore.application.command.CashOutCommand;
import com.geekersjoel237.koracore.application.command.TransferCommand;
import com.geekersjoel237.koracore.application.service.AuthServiceImpl;
import com.geekersjoel237.koracore.application.service.PaymentServiceImpl;
import com.geekersjoel237.koracore.domain.enums.OperationType;
import com.geekersjoel237.koracore.domain.enums.Role;
import com.geekersjoel237.koracore.domain.enums.UserStatus;
import com.geekersjoel237.koracore.domain.exception.*;
import com.geekersjoel237.koracore.domain.model.*;
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
import java.util.List;

import static com.geekersjoel237.koracore.domain.model.state.TransactionState.COMPLETED;
import static com.geekersjoel237.koracore.domain.model.state.TransactionState.FAILED;
import static com.geekersjoel237.koracore.shared.inmemory.InMemoryProviderSimulator.Behavior.FAIL;
import static com.geekersjoel237.koracore.shared.inmemory.InMemoryProviderSimulator.Behavior.SUCCESS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentServiceTest {

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

    private final CustomerPinEncoder pinEncoder = new BCryptCustomerPinEncoder();

    private InMemoryAccountRepository accountRepo;
    private InMemoryCustomerRepository customerRepo;
    private InMemoryTransactionRepository transactionRepo;
    private InMemoryTrxHistoricStatesRepository historicRepo;
    private InMemoryOtpStore otpStore;
    private InMemoryProviderSimulator provider;
    private AuthServiceImpl authService;
    private PaymentServiceImpl paymentService;
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
        provider = new InMemoryProviderSimulator(SUCCESS);
        ledgerRepository = new InMemoryLedgerRepository(Ledger.create(Id.generate()));
        authService = new AuthServiceImpl(
                new InMemoryUserRepository(), customerRepo, otpStore, pinEncoder, Clock.systemUTC());
        paymentService = new PaymentServiceImpl(
                authService, accountRepo, customerRepo,
                transactionRepo, historicRepo, provider, ledgerRepository);

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
        assertEquals(2, history.size());
        assertEquals("INITIALIZED", history.get(0).snapshot().oldState());
        assertEquals("PENDING", history.get(0).snapshot().newState());
        assertEquals("PENDING", history.get(1).snapshot().oldState());
        assertEquals("COMPLETED", history.get(1).snapshot().newState());

        Account customerAccount = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
        assertTrue(AMOUNT_10K.equals(customerAccount.snapshot().balance().solde()));
    }

    @Test
    void should_fail_cash_in_and_keep_balance_unchanged_when_provider_fails() {
        provider.setBehavior(FAIL);
        CashInCommand cmd = new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD);
        Transaction tx = paymentService.cashIn(cmd);

        assertEquals(FAILED, tx.snapshot().state());
        assertEquals(4, tx.snapshot().operations().size());

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
    }

    @Test
    void should_fail_cash_out_and_restore_balance_when_provider_fails() {
        paymentService.cashIn(new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
        provider.setBehavior(FAIL);

        Transaction tx = paymentService.cashOut(
                new CashOutCommand(CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD));

        assertEquals(FAILED, tx.snapshot().state());
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

        Transaction tx = paymentService.transfer(new TransferCommand(
                CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD, phoneNumberB().fullNumber()));

        assertEquals(COMPLETED, tx.snapshot().state());

        Account accountA = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
        Account accountB = accountRepo.findByCustomerId(CUST_ID_B).orElseThrow();
        assertTrue(AMOUNT_5K.equals(accountA.snapshot().balance().solde()));
        assertTrue(AMOUNT_5K.equals(accountB.snapshot().balance().solde()));

        Amount sumAB = accountA.snapshot().balance().solde()
                .add(accountB.snapshot().balance().solde());
        assertTrue(AMOUNT_10K.equals(sumAB));
    }

    @Test
    void should_fail_transfer_and_restore_balances_when_provider_fails() {
        preloadCustomerB();
        paymentService.cashIn(new CashInCommand(CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
        provider.setBehavior(FAIL);

        Transaction tx = paymentService.transfer(new TransferCommand(
                CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD, phoneNumberB().fullNumber()));

        assertEquals(FAILED, tx.snapshot().state());
        Account accountA = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
        Account accountB = accountRepo.findByCustomerId(CUST_ID_B).orElseThrow();
        assertTrue(AMOUNT_10K.equals(accountA.snapshot().balance().solde()));
        assertTrue(AMOUNT_ZERO.equals(accountB.snapshot().balance().solde()));
        assertDoubleEntryInvariant();
    }

    @Test
    void should_throw_account_not_found_when_recipient_phone_unknown() {
        assertThatThrownBy(() -> paymentService.transfer(new TransferCommand(
                CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD, "+2250000000000")))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void should_throw_account_suspended_when_recipient_is_suspended() {
        preloadCustomerB();
        suspendCustomerB();
        assertThatThrownBy(() -> paymentService.transfer(new TransferCommand(
                CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD, phoneNumberB().fullNumber())))
                .isInstanceOf(AccountSuspendedException.class);
    }

    @Test
    void should_throw_self_transfer_exception_when_sender_equals_recipient() {
        assertThatThrownBy(() -> paymentService.transfer(new TransferCommand(
                CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD, phoneNumberA().fullNumber())))
                .isInstanceOf(SelfTransferException.class);
    }

    @Test
    void should_throw_insufficient_funds_exception_when_balance_too_low_for_transfer() {
        preloadCustomerB();
        assertThatThrownBy(() -> paymentService.transfer(new TransferCommand(
                CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD, phoneNumberB().fullNumber())))
                .isInstanceOf(InsufficientFundsException.class);
    }
}