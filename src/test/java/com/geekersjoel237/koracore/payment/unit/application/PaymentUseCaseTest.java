package com.geekersjoel237.koracore.payment.unit.application;

import com.geekersjoel237.koracore.payment.application.command.CashInCommand;
import com.geekersjoel237.koracore.payment.application.command.CashOutCommand;
import com.geekersjoel237.koracore.payment.application.command.ReversePaymentCommand;
import com.geekersjoel237.koracore.payment.application.command.TransferCommand;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.LedgerEntryType;
import com.geekersjoel237.koracore.payment.domain.enums.ProviderOperationType;
import com.geekersjoel237.koracore.auth.domain.enums.Role;
import com.geekersjoel237.koracore.auth.domain.enums.UserStatus;
import com.geekersjoel237.koracore.payment.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.payment.domain.exception.AccountSuspendedException;
import com.geekersjoel237.koracore.auth.domain.exception.CustomerNotFoundException;
import com.geekersjoel237.koracore.payment.domain.exception.InsufficientFundsException;
import com.geekersjoel237.koracore.payment.domain.exception.InvalidStateTransitionException;
import com.geekersjoel237.koracore.auth.domain.exception.PinValidationException;
import com.geekersjoel237.koracore.payment.domain.exception.SelfTransferException;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.auth.domain.model.Customer;
import com.geekersjoel237.koracore.payment.domain.model.Ledger;
import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.payment.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.ports.out.security.CustomerPinEncoder;
import com.geekersjoel237.koracore.payment.ports.out.repository.LedgerRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.auth.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.auth.config.SecurityProperties;
import com.geekersjoel237.koracore.auth.adapters.out.security.BCryptCustomerPinEncoder;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryAccountRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryAuthorizationRecordRepository;
import com.geekersjoel237.koracore.auth.unit.doubles.InMemoryCustomerRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryLedgerRepository;
import com.geekersjoel237.koracore.shared.unit.doubles.InMemoryMailPort;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryProviderAdapter;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryTransactionRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryTrxHistoricStatesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;
import com.geekersjoel237.koracore.payment.application.result.PaymentResult;
import com.geekersjoel237.koracore.payment.application.query.balance.BalanceResult;
import com.geekersjoel237.koracore.payment.ports.in.CashInCommandHandler;
import com.geekersjoel237.koracore.payment.ports.in.CashOutCommandHandler;
import com.geekersjoel237.koracore.payment.application.query.balance.BalanceQuery;
import com.geekersjoel237.koracore.payment.ports.in.BalanceQueryHandler;
import com.geekersjoel237.koracore.payment.ports.in.ReversePaymentCommandHandler;
import com.geekersjoel237.koracore.payment.ports.in.TransferCommandHandler;
import com.geekersjoel237.koracore.shared.unit.doubles.DirectTransactionBoundary;
import com.geekersjoel237.koracore.payment.unit.application.UseCaseFixtures;
import com.geekersjoel237.koracore.shared.domain.vo.Msisdn;

import static com.geekersjoel237.koracore.payment.domain.model.state.TransactionState.AUTHORIZATION_FAILED;
import static com.geekersjoel237.koracore.payment.domain.model.state.TransactionState.COMPLETED;
import static com.geekersjoel237.koracore.payment.domain.model.state.TransactionState.REVERSED;
import static com.geekersjoel237.koracore.payment.unit.doubles.InMemoryProviderAdapter.Behavior.FAIL;
import static com.geekersjoel237.koracore.payment.unit.doubles.InMemoryProviderAdapter.Behavior.SUCCESS;
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
    private static final Pin RAW_PIN = Pin.of("123456");
    private static final PaymentMethod PAYMENT_METHOD = PaymentMethod.MOBILE_MONEY;
    private static final Amount AMOUNT_10K = Amount.of(BigDecimal.valueOf(10_000), "XAF");
    private static final Amount AMOUNT_5K = Amount.of(BigDecimal.valueOf(5_000), "XAF");
    private static final Amount AMOUNT_ZERO = Amount.of(BigDecimal.ZERO, "XAF");

    private static final SecurityProperties TEST_SECURITY = new SecurityProperties(
            new SecurityProperties.Jwt("test-secret-key-must-be-at-least-32-chars!!", 15, 7),
            new SecurityProperties.Otp(5)
    );

    private final CustomerPinEncoder pinEncoder = new BCryptCustomerPinEncoder();

    private InMemoryAccountRepository accountRepo;
    private InMemoryCustomerRepository customerRepo;
    private InMemoryTransactionRepository transactionRepo;
    private InMemoryTrxHistoricStatesRepository historicRepo;
    private InMemoryAuthorizationRecordRepository authorizationRecordRepo;
    private InMemoryProviderAdapter provider;
    private InMemoryMailPort mailPort;
    private CashInCommandHandler cashIn;
    private CashOutCommandHandler cashOut;
    private TransferCommandHandler transfer;
    private ReversePaymentCommandHandler reversePayment;
    private BalanceQueryHandler getBalance;
    private LedgerRepository ledgerRepository;

    private static PhoneNumber phoneNumberA() {
        return PhoneNumber.of("+237", "600000001");
    }

    /** The aggregate no longer comes back from the port; read it from the store. */
    private Transaction persisted(PaymentResult result) {
        return transactionRepo.findById(new Id(result.transactionId())).orElseThrow();
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
        provider = new InMemoryProviderAdapter(SUCCESS);
        provider.reset();
        ledgerRepository = new InMemoryLedgerRepository(Ledger.create(Id.generate()));
        mailPort = new InMemoryMailPort();
        authorizationRecordRepo = new InMemoryAuthorizationRecordRepository();
        var useCases = UseCaseFixtures.build(
                new DirectTransactionBoundary(), accountRepo, customerRepo, provider,
                transactionRepo, historicRepo, ledgerRepository,
                authorizationRecordRepo, pinEncoder);
        cashIn = useCases.cashIn();
        cashOut = useCases.cashOut();
        transfer = useCases.transfer();
        reversePayment = useCases.reversePayment();
        getBalance = useCases.getBalance();

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
                .flatMap(tx -> tx.entries().stream())
                .filter(op -> op.snapshot().type() == LedgerEntryType.DEBIT)
                .map(op -> op.snapshot().amount())
                .reduce(AMOUNT_ZERO, Amount::add);

        Amount totalCredit = transactionRepo.findAll().stream()
                .flatMap(tx -> tx.entries().stream())
                .filter(op -> op.snapshot().type() == LedgerEntryType.CREDIT)
                .map(op -> op.snapshot().amount())
                .reduce(AMOUNT_ZERO, Amount::add);

        assertTrue(totalDebit.equals(totalCredit),
                "Double-entry violated: DEBIT=" + totalDebit.value()
                        + " CREDIT=" + totalCredit.value());
    }

    // ── Groupe 1 — cashIn ─────────────────────────────────────────────────────

    @Test
    void should_complete_cash_in_when_provider_succeeds() {
        CashInCommand cmd = new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD);
        PaymentResult result = cashIn.execute(cmd);

        assertEquals(COMPLETED.name(), result.state());
        assertEquals(2, persisted(result).snapshot().entries().size());
        assertEquals(1, transactionRepo.count());

        List<TrxStateHistoric> history =
                historicRepo.findByTransactionId(new Id(result.transactionId()));
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
        CashInCommand cmd = new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD);
        PaymentResult result = cashIn.execute(cmd);

        assertEquals(AUTHORIZATION_FAILED.name(), result.state());
        assertEquals(0, persisted(result).snapshot().entries().size());

        List<TrxStateHistoric> history =
                historicRepo.findByTransactionId(new Id(result.transactionId()));
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
        CashInCommand cmd = new CashInCommand(Id.generate(), CUST_ID_A, Pin.of("wrong"), AMOUNT_10K, PAYMENT_METHOD);
        assertThatThrownBy(() -> cashIn.execute(cmd))
                .isInstanceOf(PinValidationException.class);
        assertEquals(0, transactionRepo.count());
    }

    @Test
    void should_throw_customer_not_found_exception_and_persist_nothing_when_account_missing() {
        CashInCommand cmd = new CashInCommand(Id.generate(), new Id("ghost"), RAW_PIN, AMOUNT_10K, PAYMENT_METHOD);
        assertThatThrownBy(() -> cashIn.execute(cmd))
                .isInstanceOf(CustomerNotFoundException.class);
        assertEquals(0, transactionRepo.count());
    }

    @Test
    void should_throw_account_suspended_exception_and_persist_nothing_when_customer_suspended() {
        suspendCustomerA();
        CashInCommand cmd = new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD);
        assertThatThrownBy(() -> cashIn.execute(cmd))
                .isInstanceOf(AccountSuspendedException.class);
        assertEquals(0, transactionRepo.count());
    }

    @Test
    void should_throw_illegal_argument_exception_and_persist_nothing_when_amount_is_zero() {
        CashInCommand cmd = new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_ZERO, PAYMENT_METHOD);
        assertThatThrownBy(() -> cashIn.execute(cmd))
                .isInstanceOf(IllegalArgumentException.class);
        assertEquals(0, transactionRepo.count());
    }

    // ── Groupe 2 — cashOut ────────────────────────────────────────────────────

    @Test
    void should_complete_cash_out_when_provider_succeeds() {
        cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));

        PaymentResult result = cashOut.execute(
                new CashOutCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD));

        assertEquals(COMPLETED.name(), result.state());
        Account account = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
        assertTrue(AMOUNT_5K.equals(account.snapshot().balance().solde()));

        assertThat(provider.getLastOperationType()).isEqualTo(ProviderOperationType.DISBURSEMENT);
    }

    @Test
    void should_fail_cash_out_and_restore_balance_when_provider_fails() {
        cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
        provider.setBehavior(FAIL);

        PaymentResult result = cashOut.execute(
                new CashOutCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD));

        assertEquals(AUTHORIZATION_FAILED.name(), result.state());
        Account account = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
        assertTrue(AMOUNT_10K.equals(account.snapshot().balance().solde()));
        assertDoubleEntryInvariant();
    }

    @Test
    void should_throw_insufficient_funds_exception_and_persist_nothing_when_balance_too_low() {
        assertThatThrownBy(() -> cashOut.execute(
                new CashOutCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD)))
                .isInstanceOf(InsufficientFundsException.class);
        assertEquals(0, transactionRepo.count());
    }

    @Test
    void should_throw_pin_validation_exception_and_persist_nothing_on_cash_out_wrong_pin() {
        cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
        assertThatThrownBy(() -> cashOut.execute(
                new CashOutCommand(Id.generate(), CUST_ID_A, Pin.of("wrong"), AMOUNT_5K, PAYMENT_METHOD)))
                .isInstanceOf(PinValidationException.class);
        assertEquals(1, transactionRepo.count());
    }

    // ── Groupe 3 — transfer ───────────────────────────────────────────────────

    @Test
    void should_complete_transfer_and_update_both_balances_when_provider_succeeds() {
        preloadCustomerB();
        cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
        provider.reset(); // isolate transfer — verify it makes no provider call

        PaymentResult result = transfer.execute(new TransferCommand(Id.generate(), 
                CUST_ID_A, RAW_PIN, AMOUNT_5K, Msisdn.of(phoneNumberB().fullNumber())));

        assertEquals(COMPLETED.name(), result.state());

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
        cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD));

        assertThatThrownBy(() -> transfer.execute(new TransferCommand(Id.generate(), 
                CUST_ID_A, RAW_PIN, AMOUNT_10K, Msisdn.of(phoneNumberB().fullNumber()))))
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
        cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));

        PaymentResult result = transfer.execute(new TransferCommand(Id.generate(), 
                CUST_ID_A, RAW_PIN, AMOUNT_5K, Msisdn.of(phoneNumberB().fullNumber())));

        assertEquals(COMPLETED.name(), result.state());

        List<TrxStateHistoric> history =
                historicRepo.findByTransactionId(new Id(result.transactionId()));
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
                .findActiveByTransactionId(new Id(result.transactionId())).isEmpty());
    }

    @Test
    void should_throw_account_not_found_when_recipient_phone_unknown() {
        assertThatThrownBy(() -> transfer.execute(new TransferCommand(Id.generate(), 
                CUST_ID_A, RAW_PIN, AMOUNT_5K, Msisdn.of("+2250000000000"))))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void should_throw_account_suspended_when_recipient_is_suspended() {
        preloadCustomerB();
        suspendCustomerB();
        assertThatThrownBy(() -> transfer.execute(new TransferCommand(Id.generate(), 
                CUST_ID_A, RAW_PIN, AMOUNT_5K, Msisdn.of(phoneNumberB().fullNumber()))))
                .isInstanceOf(AccountSuspendedException.class);
    }

    @Test
    void should_throw_self_transfer_exception_when_sender_equals_recipient() {
        assertThatThrownBy(() -> transfer.execute(new TransferCommand(Id.generate(), 
                CUST_ID_A, RAW_PIN, AMOUNT_5K, Msisdn.of(phoneNumberA().fullNumber()))))
                .isInstanceOf(SelfTransferException.class);
    }

    @Test
    void should_throw_insufficient_funds_exception_when_balance_too_low_for_transfer() {
        preloadCustomerB();
        assertThatThrownBy(() -> transfer.execute(new TransferCommand(Id.generate(), 
                CUST_ID_A, RAW_PIN, AMOUNT_5K, Msisdn.of(phoneNumberB().fullNumber()))))
                .isInstanceOf(InsufficientFundsException.class);
    }

    // ── Groupe 4 — getBalance ─────────────────────────────────────────────────

    @Test
    void should_return_zero_balance_for_new_customer_account() {
        BalanceResult balance = getBalance.execute(new BalanceQuery(CUST_ID_A));

        assertTrue(AMOUNT_ZERO.equals(balance.balance()));
    }

    @Test
    void should_return_balance_reflecting_cash_in() {
        cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));

        BalanceResult balance = getBalance.execute(new BalanceQuery(CUST_ID_A));

        assertTrue(AMOUNT_10K.equals(balance.balance()));
    }

    @Test
    void should_return_balance_reflecting_cash_in_then_cash_out() {
        cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
        cashOut.execute(new CashOutCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD));

        BalanceResult balance = getBalance.execute(new BalanceQuery(CUST_ID_A));

        assertTrue(AMOUNT_5K.equals(balance.balance()));
    }

    @Test
    void should_throw_account_not_found_when_customer_has_no_account() {
        assertThatThrownBy(() -> getBalance.execute(new BalanceQuery(new Id("ghost-customer"))))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ── Groupe 5 — reversal ───────────────────────────────────────────────────

    @Nested
    class ReversalTests {

        @Test
        void should_debit_customer_and_restore_zero_balance_when_cash_in_reversed() {
            PaymentResult result = cashIn.execute(
                    new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));

            Account before = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
            assertTrue(AMOUNT_10K.equals(before.snapshot().balance().solde()));

            reversePayment.execute(new ReversePaymentCommand(
                    new Id(result.transactionId()), new Id("op-001"), "OPERATOR", "chargeback", new Id("corr-001")));

            Account after = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
            assertTrue(AMOUNT_ZERO.equals(after.snapshot().balance().solde()));
        }

        @Test
        void should_credit_customer_and_restore_original_balance_when_cash_out_reversed() {
            cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
            PaymentResult cashOutResult = cashOut.execute(
                    new CashOutCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD));

            Account midway = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
            assertTrue(AMOUNT_5K.equals(midway.snapshot().balance().solde()));

            reversePayment.execute(new ReversePaymentCommand(
                    new Id(cashOutResult.transactionId()), new Id("op-001"), "OPERATOR", "dispute", new Id("corr-002")));

            Account after = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
            assertTrue(AMOUNT_10K.equals(after.snapshot().balance().solde()));
        }

        @Test
        void should_credit_sender_and_debit_receiver_when_p2p_reversed() {
            preloadCustomerB();
            cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
            PaymentResult transferResult = transfer.execute(new TransferCommand(Id.generate(), 
                    CUST_ID_A, RAW_PIN, AMOUNT_5K, Msisdn.of(phoneNumberB().fullNumber())));

            reversePayment.execute(new ReversePaymentCommand(
                    new Id(transferResult.transactionId()), new Id("op-001"), "OPERATOR", "fraud", new Id("corr-003")));

            Account accountA = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
            Account accountB = accountRepo.findByCustomerId(CUST_ID_B).orElseThrow();
            assertTrue(AMOUNT_10K.equals(accountA.snapshot().balance().solde()),
                    "Sender should be back to 10K after reversal");
            assertTrue(AMOUNT_ZERO.equals(accountB.snapshot().balance().solde()),
                    "Receiver should be back to 0 after reversal");
        }

        @Test
        void double_entry_invariant_holds_across_cash_in_and_reversal() {
            PaymentResult result = cashIn.execute(
                    new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));

            reversePayment.execute(new ReversePaymentCommand(
                    new Id(result.transactionId()), new Id("op-001"), "OPERATOR", "test", new Id("corr-004")));

            assertDoubleEntryInvariant();
        }

        @Test
        void should_throw_invalid_state_transition_when_reversing_already_reversed_transaction() {
            PaymentResult result = cashIn.execute(
                    new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));

            reversePayment.execute(new ReversePaymentCommand(
                    new Id(result.transactionId()), new Id("op-001"), "OPERATOR", "first", new Id("corr-005")));

            Transaction reversed = transactionRepo.findById(new Id(result.transactionId())).orElseThrow();
            assertEquals(REVERSED, reversed.snapshot().state());

            assertThatThrownBy(() -> reversePayment.execute(new ReversePaymentCommand(
                    new Id(result.transactionId()), new Id("op-001"), "OPERATOR", "second", new Id("corr-006"))))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }
}