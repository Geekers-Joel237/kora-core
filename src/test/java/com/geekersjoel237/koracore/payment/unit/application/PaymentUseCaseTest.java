package com.geekersjoel237.koracore.payment.unit.application;

import com.geekersjoel237.koracore.payment.application.command.cashIn.CashInCommand;
import com.geekersjoel237.koracore.payment.application.command.cashOut.CashOutCommand;
import com.geekersjoel237.koracore.payment.application.command.reversePayment.ReversePaymentCommand;
import com.geekersjoel237.koracore.payment.application.command.transfer.TransferCommand;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.LedgerEntryType;
import com.geekersjoel237.koracore.payment.domain.enums.ProviderOperationType;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.auth.domain.enums.Role;
import com.geekersjoel237.koracore.auth.domain.enums.UserStatus;
import com.geekersjoel237.koracore.payment.application.command.expireAuthorizations.ExpireAuthorizationsCommand;
import com.geekersjoel237.koracore.payment.domain.enums.TriggerSource;
import com.geekersjoel237.koracore.payment.domain.exception.AccountBlockedException;
import com.geekersjoel237.koracore.payment.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.payment.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.payment.domain.exception.AccountSuspendedException;
import com.geekersjoel237.koracore.auth.domain.exception.CustomerNotFoundException;
import com.geekersjoel237.koracore.payment.domain.exception.InsufficientFundsException;
import com.geekersjoel237.koracore.payment.domain.exception.InvalidStateTransitionException;
import com.geekersjoel237.koracore.auth.domain.exception.PinValidationException;
import com.geekersjoel237.koracore.payment.domain.exception.SelfTransferException;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.domain.model.AuthorizationRecord;
import com.geekersjoel237.koracore.auth.domain.model.Customer;
import com.geekersjoel237.koracore.payment.domain.model.Ledger;
import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.payment.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.ports.out.security.CustomerPinEncoder;
import com.geekersjoel237.koracore.payment.ports.out.repository.LedgerRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.auth.config.SecurityProperties;
import com.geekersjoel237.koracore.auth.adapters.out.security.BCryptCustomerPinEncoder;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryAccountRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryAuthorizationRecordRepository;
import com.geekersjoel237.koracore.auth.unit.doubles.InMemoryCustomerRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryLedgerRepository;
import com.geekersjoel237.koracore.shared.unit.doubles.InMemoryMailPort;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryPaymentProviderAdapter;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryTransactionRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryTrxHistoricStatesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;
import com.geekersjoel237.koracore.payment.application.command.cashOut.CashOutResult;
import com.geekersjoel237.koracore.payment.application.query.balance.BalanceResult;
import com.geekersjoel237.koracore.payment.ports.in.CashInCommandHandler;
import com.geekersjoel237.koracore.payment.ports.in.CashOutCommandHandler;
import com.geekersjoel237.koracore.payment.application.query.balance.BalanceQuery;
import com.geekersjoel237.koracore.payment.ports.in.BalanceQueryHandler;
import com.geekersjoel237.koracore.payment.ports.in.ReversePaymentCommandHandler;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryFundsHoldRepository;
import com.geekersjoel237.koracore.payment.domain.model.FundsHold;
import com.geekersjoel237.koracore.payment.ports.in.TransferCommandHandler;
import com.geekersjoel237.koracore.shared.unit.doubles.DirectTransactionBoundary;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;

import static com.geekersjoel237.koracore.payment.domain.model.state.TransactionState.AUTHORIZATION_FAILED;
import static com.geekersjoel237.koracore.payment.domain.model.state.TransactionState.CAPTURE_FAILED;
import static com.geekersjoel237.koracore.payment.domain.model.state.TransactionState.COMPLETED;
import static com.geekersjoel237.koracore.payment.domain.model.state.TransactionState.REVERSED;
import static com.geekersjoel237.koracore.payment.domain.model.state.TransactionState.UNKNOWN;
import static com.geekersjoel237.koracore.payment.unit.doubles.InMemoryPaymentProviderAdapter.Behavior.FAIL;
import static com.geekersjoel237.koracore.payment.unit.doubles.InMemoryPaymentProviderAdapter.Behavior.FAIL_ON_CAPTURE;
import static com.geekersjoel237.koracore.payment.unit.doubles.InMemoryPaymentProviderAdapter.Behavior.SUCCESS;
import static com.geekersjoel237.koracore.payment.unit.doubles.InMemoryPaymentProviderAdapter.Behavior.TIMEOUT_ON_AUTHORIZE;
import static com.geekersjoel237.koracore.payment.unit.doubles.InMemoryPaymentProviderAdapter.Behavior.TIMEOUT_ON_CAPTURE;
import static com.geekersjoel237.koracore.payment.unit.doubles.InMemoryPaymentProviderAdapter.Behavior.TIMEOUT_ON_REVERSE;
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
            new SecurityProperties.Jwt(
                    "test-access-key-must-be-at-least-32-chars!!",
                    "test-refresh-key-must-be-at-least-32-chars!", 15, 7),
            new SecurityProperties.Otp(5)
    );

    private final CustomerPinEncoder pinEncoder = new BCryptCustomerPinEncoder();

    private InMemoryAccountRepository accountRepo;
    private InMemoryCustomerRepository customerRepo;
    private InMemoryTransactionRepository transactionRepo;
    private InMemoryTrxHistoricStatesRepository historicRepo;
    private InMemoryAuthorizationRecordRepository authorizationRecordRepo;
    private InMemoryPaymentProviderAdapter provider;
    private InMemoryMailPort mailPort;
    private CashInCommandHandler cashIn;
    private CashOutCommandHandler cashOut;
    private TransferCommandHandler transfer;
    private ReversePaymentCommandHandler reversePayment;
    private InMemoryFundsHoldRepository holdRepo;
    private BalanceQueryHandler getBalance;
    private LedgerRepository ledgerRepository;

    private static PhoneNumber phoneNumberA() {
        return PhoneNumber.of("+237", "600000001");
    }

    private Transaction persisted(String transactionId) {
        return transactionRepo.findById(new Id(transactionId)).orElseThrow();
    }

    private static PhoneNumber phoneNumberB() {
        return PhoneNumber.of("+237", "600000002");
    }

    @BeforeEach
    void setUp() {
        holdRepo = new InMemoryFundsHoldRepository();
        accountRepo = new InMemoryAccountRepository(holdRepo);
        customerRepo = new InMemoryCustomerRepository();
        historicRepo = new InMemoryTrxHistoricStatesRepository();
        transactionRepo = new InMemoryTransactionRepository(historicRepo);
        provider = new InMemoryPaymentProviderAdapter(SUCCESS);
        provider.reset();
        ledgerRepository = new InMemoryLedgerRepository(Ledger.create(Id.generate()));
        mailPort = new InMemoryMailPort();
        authorizationRecordRepo = new InMemoryAuthorizationRecordRepository();
        var useCases = UseCaseFixtures.build(
                new DirectTransactionBoundary(), accountRepo, customerRepo, provider,
                transactionRepo, historicRepo, ledgerRepository,
                authorizationRecordRepo, holdRepo, pinEncoder);
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

    @Test
    void should_complete_cash_in_when_provider_succeeds() {
        CashInCommand cmd = new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD);
        var result = cashIn.execute(cmd);

        assertEquals(COMPLETED.name(), result.state());
        assertEquals(2, persisted(result.transactionId()).snapshot().entries().size());
        assertEquals(1, transactionRepo.count());

        List<TrxStateHistoric> history =
                persisted(result.transactionId()).history();
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
        var result = cashIn.execute(cmd);

        assertEquals(AUTHORIZATION_FAILED.name(), result.state());
        assertEquals(0, persisted(result.transactionId()).snapshot().entries().size());

        List<TrxStateHistoric> history =
                persisted(result.transactionId()).history();
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

    @Test
    void should_complete_cash_out_when_provider_succeeds() {
        cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));

        var result = cashOut.execute(
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

        var result = cashOut.execute(
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

    @Test
    void should_complete_transfer_and_update_both_balances_when_provider_succeeds() {
        preloadCustomerB();
        cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
        provider.reset();

        var result = transfer.execute(new TransferCommand(Id.generate(), 
                CUST_ID_A, RAW_PIN, AMOUNT_5K, phoneNumberB()));

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
                CUST_ID_A, RAW_PIN, AMOUNT_10K, phoneNumberB())))
                .isInstanceOf(InsufficientFundsException.class);

        assertEquals(1, transactionRepo.count());
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

        var result = transfer.execute(new TransferCommand(Id.generate(), 
                CUST_ID_A, RAW_PIN, AMOUNT_5K, phoneNumberB()));

        assertEquals(COMPLETED.name(), result.state());

        List<TrxStateHistoric> history =
                persisted(result.transactionId()).history();
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
                CUST_ID_A, RAW_PIN, AMOUNT_5K, PhoneNumber.of("+225", "0000000000"))))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void should_throw_account_suspended_when_recipient_is_suspended() {
        preloadCustomerB();
        suspendCustomerB();
        assertThatThrownBy(() -> transfer.execute(new TransferCommand(Id.generate(), 
                CUST_ID_A, RAW_PIN, AMOUNT_5K, phoneNumberB())))
                .isInstanceOf(AccountSuspendedException.class);
    }

    @Test
    void should_throw_self_transfer_exception_when_sender_equals_recipient() {
        // Funded first: an empty wallet is refused on funds before the self-transfer check.
        cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));

        assertThatThrownBy(() -> transfer.execute(new TransferCommand(Id.generate(),
                CUST_ID_A, RAW_PIN, AMOUNT_5K, phoneNumberA())))
                .isInstanceOf(SelfTransferException.class);
    }

    @Test
    void should_throw_insufficient_funds_exception_when_balance_too_low_for_transfer() {
        preloadCustomerB();
        assertThatThrownBy(() -> transfer.execute(new TransferCommand(Id.generate(), 
                CUST_ID_A, RAW_PIN, AMOUNT_5K, phoneNumberB())))
                .isInstanceOf(InsufficientFundsException.class);
    }

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

    @Nested
    class ReversalTests {

        @Test
        void should_debit_customer_and_restore_zero_balance_when_cash_in_reversed() {
            var result = cashIn.execute(
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
            var cashOutResult = cashOut.execute(
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
            var transferResult = transfer.execute(new TransferCommand(Id.generate(), 
                    CUST_ID_A, RAW_PIN, AMOUNT_5K, phoneNumberB()));

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
            var result = cashIn.execute(
                    new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));

            reversePayment.execute(new ReversePaymentCommand(
                    new Id(result.transactionId()), new Id("op-001"), "OPERATOR", "test", new Id("corr-004")));

            assertDoubleEntryInvariant();
        }

        @Test
        void should_throw_invalid_state_transition_when_reversing_already_reversed_transaction() {
            var result = cashIn.execute(
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

    @Nested
    class FundsHoldTests {

        private Id walletA() {
            return accountRepo.findByCustomerId(CUST_ID_A).orElseThrow().snapshot().accountId();
        }

        private Amount heldOnWalletA() {
            return accountRepo.findByCustomerId(CUST_ID_A).orElseThrow().snapshot().reserved();
        }

        private void holdOnWalletA(Amount amount) {
            holdRepo.save(FundsHold.place(Id.generate(), walletA(), amount));
        }

        private CashOutResult cashOut(Amount amount) {
            return cashOut.execute(new CashOutCommand(Id.generate(), CUST_ID_A, RAW_PIN, amount, PAYMENT_METHOD));
        }

        @BeforeEach
        void fundWalletA() {
            cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
        }

        @Test
        void a_completed_cash_out_leaves_no_active_hold() {
            var result = cashOut(AMOUNT_5K);

            assertEquals(COMPLETED.name(), result.state());
            assertTrue(AMOUNT_ZERO.equals(heldOnWalletA()));
        }

        @Test
        void a_cash_out_refused_at_authorization_releases_its_hold() {
            provider.setBehavior(FAIL);

            var result = cashOut(AMOUNT_5K);

            assertEquals(AUTHORIZATION_FAILED.name(), result.state());
            assertTrue(AMOUNT_ZERO.equals(heldOnWalletA()));
        }

        @Test
        void a_cash_out_failed_at_capture_releases_its_hold() {
            provider.setBehavior(FAIL_ON_CAPTURE);

            cashOut(AMOUNT_5K);

            assertTrue(AMOUNT_ZERO.equals(heldOnWalletA()));
        }

        @Test
        void a_cash_out_cannot_spend_held_funds() {
            holdOnWalletA(AMOUNT_10K);

            assertThatThrownBy(() -> cashOut(AMOUNT_5K))
                    .isInstanceOf(InsufficientFundsException.class);
        }

        @Test
        void a_transfer_cannot_spend_held_funds() {
            preloadCustomerB();
            holdOnWalletA(AMOUNT_10K);

            assertThatThrownBy(() -> transfer.execute(new TransferCommand(Id.generate(),
                    CUST_ID_A, RAW_PIN, AMOUNT_5K, phoneNumberB())))
                    .isInstanceOf(InsufficientFundsException.class);
        }

        @Test
        void the_balance_reports_what_is_available_beside_the_ledger_balance() {
            holdOnWalletA(AMOUNT_5K);

            BalanceResult balance = getBalance.execute(new BalanceQuery(CUST_ID_A));

            assertTrue(AMOUNT_10K.equals(balance.balance()));
            assertTrue(AMOUNT_5K.equals(balance.available()));
        }

        @Test
        void a_reversal_cannot_take_back_funds_held_by_an_in_flight_debit() {
            var deposit = cashIn.execute(
                    new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
            holdOnWalletA(Amount.of(BigDecimal.valueOf(15_000), "XAF"));

            assertThatThrownBy(() -> reversePayment.execute(new ReversePaymentCommand(
                    new Id(deposit.transactionId()), new Id("op-001"), "OPERATOR", "chargeback", Id.generate())))
                    .isInstanceOf(InsufficientFundsException.class);
        }
    }
    @Nested
    class ProviderTimeoutTests {

        private Amount heldOnWalletA() {
            return accountRepo.findByCustomerId(CUST_ID_A).orElseThrow().snapshot().reserved();
        }

        private Amount balanceOfWalletA() {
            return accountRepo.findByCustomerId(CUST_ID_A).orElseThrow().snapshot().balance().solde();
        }

        private AuthorizationRecord activeAuthorization(Id transactionId) {
            return authorizationRecordRepo.findActiveByTransactionId(transactionId).orElseThrow();
        }

        private Id authorizedCashOut(Amount amount) {
            Account wallet = accountRepo.findByCustomerId(CUST_ID_A).orElseThrow();
            Account floatAccount = accountRepo.findFloatByProviderId(PROVIDER_ID).orElseThrow();
            Ledger ledger = ledgerRepository.findFirst();
            Transaction tx = ledger.initiate(wallet, floatAccount, TransactionType.CASH_OUT, PAYMENT_METHOD, amount);
            FundsHold hold = ledger.hold(tx, wallet);
            tx.authorize();
            transactionRepo.save(tx);
            holdRepo.save(hold);
            Id transactionId = tx.snapshot().transactionId();
            authorizationRecordRepo.save(AuthorizationRecord.create(
                    transactionId, "provider-ref-001", amount, Duration.ofMinutes(15)));
            return transactionId;
        }

        @BeforeEach
        void fundWalletA() {
            cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
        }

        @Test
        void a_cash_in_whose_authorization_times_out_is_unknown() {
            provider.setBehavior(TIMEOUT_ON_AUTHORIZE);

            var result = cashIn.execute(
                    new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD));

            assertEquals(UNKNOWN.name(), result.state());
            assertEquals(UNKNOWN, persisted(result.transactionId()).snapshot().state());
            assertTrue(AMOUNT_10K.equals(balanceOfWalletA()));
        }

        @Test
        void a_cash_out_whose_authorization_times_out_is_unknown_and_keeps_its_hold() {
            provider.setBehavior(TIMEOUT_ON_AUTHORIZE);

            var result = cashOut.execute(
                    new CashOutCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD));

            assertEquals(UNKNOWN.name(), result.state());
            assertEquals(UNKNOWN, persisted(result.transactionId()).snapshot().state());
            assertTrue(AMOUNT_10K.equals(balanceOfWalletA()));
            assertTrue(AMOUNT_5K.equals(heldOnWalletA()));
        }

        @Test
        void a_cash_in_whose_capture_times_out_is_unknown_and_keeps_the_provider_reference() {
            provider.setBehavior(TIMEOUT_ON_CAPTURE);

            var result = cashIn.execute(
                    new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD));

            assertEquals(UNKNOWN.name(), result.state());
            assertEquals(UNKNOWN, persisted(result.transactionId()).snapshot().state());
            assertTrue(AMOUNT_10K.equals(balanceOfWalletA()));
            assertEquals(provider.lastAuthorizationReference(),
                    activeAuthorization(new Id(result.transactionId())).snapshot().providerReference());
        }

        @Test
        void a_cash_out_whose_capture_times_out_is_unknown_keeps_its_hold_and_the_provider_reference() {
            provider.setBehavior(TIMEOUT_ON_CAPTURE);

            var result = cashOut.execute(
                    new CashOutCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD));

            assertEquals(UNKNOWN.name(), result.state());
            assertEquals(UNKNOWN, persisted(result.transactionId()).snapshot().state());
            assertTrue(AMOUNT_10K.equals(balanceOfWalletA()));
            assertTrue(AMOUNT_5K.equals(heldOnWalletA()));
            assertEquals(provider.lastAuthorizationReference(),
                    activeAuthorization(new Id(result.transactionId())).snapshot().providerReference());
        }

        @Test
        void a_reversal_whose_provider_call_times_out_leaves_the_transaction_unknown() {
            Id transactionId = authorizedCashOut(AMOUNT_5K);
            provider.setBehavior(TIMEOUT_ON_REVERSE);

            var result = reversePayment.execute(new ReversePaymentCommand(
                    transactionId, new Id("op-001"), "OPERATOR", "customer dispute", Id.generate()));

            assertEquals(UNKNOWN.name(), result.state());
            assertEquals(UNKNOWN, transactionRepo.findById(transactionId).orElseThrow().snapshot().state());
            assertTrue(AMOUNT_5K.equals(heldOnWalletA()));
            assertEquals("provider-ref-001", activeAuthorization(transactionId).snapshot().providerReference());
        }
    }

    private Account walletOf(Id customerId) {
        return accountRepo.findByCustomerId(customerId).orElseThrow();
    }

    private Account floatAccount() {
        return accountRepo.findFloatByProviderId(PROVIDER_ID).orElseThrow();
    }

    private void registerCustomerWithoutWallet(Id customerId, String email, PhoneNumber phone) {
        User user = User.create(customerId, "No Wallet", email, Role.CUSTOMER);
        customerRepo.save(Customer.create(user, phone, RAW_PIN, pinEncoder));
    }

    private Id authorized(TransactionType type, Amount amount) {
        Account wallet = walletOf(CUST_ID_A);
        Ledger ledger = ledgerRepository.findFirst();
        boolean debit = type == TransactionType.CASH_OUT;
        Transaction tx = debit
                ? ledger.initiate(wallet, floatAccount(), type, PAYMENT_METHOD, amount)
                : ledger.initiate(floatAccount(), wallet, type, PAYMENT_METHOD, amount);
        if (debit) holdRepo.save(ledger.hold(tx, wallet));
        tx.authorize();
        transactionRepo.save(tx);
        Id transactionId = tx.snapshot().transactionId();
        authorizationRecordRepo.save(AuthorizationRecord.create(
                transactionId, "provider-ref-002", amount, Duration.ofMinutes(15)));
        return transactionId;
    }

    @Nested
    class RefusedBeforeAnyMoneyMoves {

        private static final Id CUST_ID_C = new Id("cust-003");
        private static final PhoneNumber PHONE_C = PhoneNumber.of("+237", "600000003");

        @Test
        void a_cash_in_to_a_customer_without_a_wallet_is_refused() {
            registerCustomerWithoutWallet(CUST_ID_C, "c@koracore.com", PHONE_C);

            assertThatThrownBy(() -> cashIn.execute(
                    new CashInCommand(Id.generate(), CUST_ID_C, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD)))
                    .isInstanceOf(AccountNotFoundException.class);
            assertEquals(0, transactionRepo.count());
        }

        @Test
        void a_cash_out_by_a_suspended_customer_is_refused() {
            cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
            suspendCustomerA();

            assertThatThrownBy(() -> cashOut.execute(
                    new CashOutCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD)))
                    .isInstanceOf(AccountSuspendedException.class);
            assertEquals(1, transactionRepo.count());
            assertTrue(AMOUNT_10K.equals(walletOf(CUST_ID_A).snapshot().balance().solde()));
        }

        @Test
        void a_cash_out_by_a_customer_without_a_wallet_is_refused() {
            registerCustomerWithoutWallet(CUST_ID_C, "c@koracore.com", PHONE_C);

            assertThatThrownBy(() -> cashOut.execute(
                    new CashOutCommand(Id.generate(), CUST_ID_C, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD)))
                    .isInstanceOf(AccountNotFoundException.class);
            assertEquals(0, transactionRepo.count());
        }

        @Test
        void a_transfer_by_a_suspended_payer_is_refused() {
            preloadCustomerB();
            cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
            suspendCustomerA();

            assertThatThrownBy(() -> transfer.execute(new TransferCommand(Id.generate(),
                    CUST_ID_A, RAW_PIN, AMOUNT_5K, phoneNumberB())))
                    .isInstanceOf(AccountSuspendedException.class);
            assertTrue(AMOUNT_ZERO.equals(walletOf(CUST_ID_B).snapshot().balance().solde()));
        }

        @Test
        void a_transfer_by_a_payer_without_a_wallet_is_refused() {
            preloadCustomerB();
            registerCustomerWithoutWallet(CUST_ID_C, "c@koracore.com", PHONE_C);

            assertThatThrownBy(() -> transfer.execute(new TransferCommand(Id.generate(),
                    CUST_ID_C, RAW_PIN, AMOUNT_5K, phoneNumberB())))
                    .isInstanceOf(AccountNotFoundException.class);
            assertEquals(0, transactionRepo.count());
        }

        @Test
        void a_transfer_to_a_customer_without_a_wallet_is_refused() {
            cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
            registerCustomerWithoutWallet(CUST_ID_C, "c@koracore.com", PHONE_C);

            assertThatThrownBy(() -> transfer.execute(new TransferCommand(Id.generate(),
                    CUST_ID_A, RAW_PIN, AMOUNT_5K, PHONE_C)))
                    .isInstanceOf(AccountNotFoundException.class);
            assertTrue(AMOUNT_10K.equals(walletOf(CUST_ID_A).snapshot().balance().solde()));
        }

        @Test
        void a_transfer_to_a_blocked_wallet_is_refused() {
            preloadCustomerB();
            cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
            Account walletB = walletOf(CUST_ID_B);
            walletB.block();
            accountRepo.save(walletB);

            assertThatThrownBy(() -> transfer.execute(new TransferCommand(Id.generate(),
                    CUST_ID_A, RAW_PIN, AMOUNT_5K, phoneNumberB())))
                    .isInstanceOf(AccountBlockedException.class);
            assertTrue(AMOUNT_10K.equals(walletOf(CUST_ID_A).snapshot().balance().solde()));
            assertTrue(AMOUNT_ZERO.equals(walletOf(CUST_ID_B).snapshot().balance().solde()));
        }

        @Test
        void no_money_moves_without_a_float_account() {
            var accountsWithoutFloat = new InMemoryAccountRepository(holdRepo);
            accountsWithoutFloat.save(Account.createCustomerAccount(Id.generate(), CUST_ID_A));
            var withoutFloat = UseCaseFixtures.build(
                    new DirectTransactionBoundary(), accountsWithoutFloat, customerRepo, provider,
                    transactionRepo, historicRepo, ledgerRepository,
                    authorizationRecordRepo, holdRepo, pinEncoder);

            assertThatThrownBy(() -> withoutFloat.cashIn().execute(
                    new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD)))
                    .isInstanceOf(AccountNotFoundException.class);
            assertThatThrownBy(() -> withoutFloat.cashOut().execute(
                    new CashOutCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_5K, PAYMENT_METHOD)))
                    .isInstanceOf(AccountNotFoundException.class);
            assertEquals(0, transactionRepo.count());
            assertThat(provider.authorizeCalls()).isZero();
        }
    }

    @Nested
    class CaptureRefusedByTheProvider {

        @Test
        void a_cash_in_whose_capture_is_refused_fails_without_crediting_the_wallet() {
            provider.setBehavior(FAIL_ON_CAPTURE);

            var result = cashIn.execute(
                    new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));

            assertEquals(CAPTURE_FAILED.name(), result.state());
            Transaction tx = persisted(result.transactionId());
            assertEquals(0, tx.snapshot().entries().size());
            assertThat(tx.history()).extracting(row -> row.snapshot().newState())
                    .containsExactly("INITIALIZED", "AUTHORIZED", "CAPTURE_FAILED");
            assertTrue(AMOUNT_ZERO.equals(walletOf(CUST_ID_A).snapshot().balance().solde()));
            assertThat(authorizationRecordRepo.findByTransactionId(new Id(result.transactionId())))
                    .get().extracting(record -> record.snapshot().status())
                    .isEqualTo(AuthorizationRecord.AuthorizationStatus.CANCELLED);
            assertDoubleEntryInvariant();
        }
    }

    @Nested
    class ReversalOfAnAuthorizedPayment {

        @BeforeEach
        void fundWalletA() {
            cashIn.execute(new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
        }

        @Test
        void reversing_an_authorized_cash_out_releases_its_hold_at_the_provider_and_on_the_wallet() {
            Id transactionId = authorized(TransactionType.CASH_OUT, AMOUNT_5K);
            assertTrue(AMOUNT_5K.equals(walletOf(CUST_ID_A).snapshot().reserved()));

            var result = reversePayment.execute(new ReversePaymentCommand(
                    transactionId, new Id("op-001"), "OPERATOR", "customer dispute", Id.generate()));

            assertEquals(REVERSED.name(), result.state());
            assertTrue(AMOUNT_ZERO.equals(walletOf(CUST_ID_A).snapshot().reserved()));
            assertTrue(AMOUNT_10K.equals(walletOf(CUST_ID_A).snapshot().balance().solde()));
            assertThat(authorizationRecordRepo.findActiveByTransactionId(transactionId)).isEmpty();
            TrxStateHistoric last = persisted(transactionId.value()).history().getLast();
            assertEquals("AUTHORIZED", last.snapshot().oldState());
            assertEquals("REVERSED", last.snapshot().newState());
            assertEquals("customer dispute", last.snapshot().notes());
            assertEquals("op-001", last.snapshot().actorId());
        }

        @Test
        void reversing_an_authorized_cash_in_cancels_it_without_touching_the_wallet() {
            Id transactionId = authorized(TransactionType.CASH_IN, AMOUNT_5K);

            var result = reversePayment.execute(new ReversePaymentCommand(
                    transactionId, new Id("op-001"), "OPERATOR", "provider dispute", Id.generate()));

            assertEquals(REVERSED.name(), result.state());
            assertTrue(AMOUNT_10K.equals(walletOf(CUST_ID_A).snapshot().balance().solde()));
            assertThat(authorizationRecordRepo.findActiveByTransactionId(transactionId)).isEmpty();
        }

        @Test
        void an_authorized_payment_whose_authorization_is_gone_cannot_be_reversed() {
            Id transactionId = authorized(TransactionType.CASH_IN, AMOUNT_5K);
            var record = authorizationRecordRepo.findActiveByTransactionId(transactionId).orElseThrow();
            record.cancel();
            authorizationRecordRepo.save(record);

            assertThatThrownBy(() -> reversePayment.execute(new ReversePaymentCommand(
                    transactionId, new Id("op-001"), "OPERATOR", "dispute", Id.generate())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(transactionId.value());
        }
    }

    @Nested
    class ReversalOfAPostedPayment {

        private Id postedCashIn(TransactionState reached) {
            Account wallet = walletOf(CUST_ID_A);
            Account floatAccount = floatAccount();
            Ledger ledger = ledgerRepository.findFirst();
            Transaction tx = ledger.initiate(floatAccount, wallet, TransactionType.CASH_IN, PAYMENT_METHOD, AMOUNT_10K);
            tx.authorize();
            ledger.post(tx, floatAccount, wallet);
            accountRepo.save(wallet);
            tx.capture();
            if (reached != TransactionState.CAPTURED) tx.pendSettlement();
            if (reached == TransactionState.SETTLED) tx.settle();
            transactionRepo.save(tx);
            return tx.snapshot().transactionId();
        }

        @ParameterizedTest
        @ValueSource(strings = {"CAPTURED", "SETTLEMENT_PENDING", "SETTLED"})
        void a_payment_posted_but_not_completed_is_compensated(String reached) {
            Id transactionId = postedCashIn(TransactionState.fromValue(reached));
            assertTrue(AMOUNT_10K.equals(walletOf(CUST_ID_A).snapshot().balance().solde()));

            var result = reversePayment.execute(new ReversePaymentCommand(
                    transactionId, new Id("op-001"), "OPERATOR", "chargeback", Id.generate()));

            assertEquals(REVERSED.name(), result.state());
            assertTrue(AMOUNT_ZERO.equals(walletOf(CUST_ID_A).snapshot().balance().solde()));
            assertEquals(4, persisted(transactionId.value()).snapshot().entries().size());
            assertDoubleEntryInvariant();
        }
    }

    @Nested
    class ReversalRefusals {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        void a_reversal_needs_a_reason(String reason) {
            var deposit = cashIn.execute(
                    new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));

            assertThatThrownBy(() -> reversePayment.execute(new ReversePaymentCommand(
                    new Id(deposit.transactionId()), new Id("op-001"), "OPERATOR", reason, Id.generate())))
                    .isInstanceOf(IllegalArgumentException.class);
            assertEquals(COMPLETED, persisted(deposit.transactionId()).snapshot().state());
            assertTrue(AMOUNT_10K.equals(walletOf(CUST_ID_A).snapshot().balance().solde()));
        }

        @Test
        void of_two_operators_reversing_the_same_payment_at_once_only_one_moves_the_money() {
            var deposit = cashIn.execute(
                    new CashInCommand(Id.generate(), CUST_ID_A, RAW_PIN, AMOUNT_10K, PAYMENT_METHOD));
            Id transactionId = new Id(deposit.transactionId());
            var interleaving = new InterleavingBoundary();
            var slowOperator = UseCaseFixtures.build(
                    interleaving, accountRepo, customerRepo, provider,
                    transactionRepo, historicRepo, ledgerRepository,
                    authorizationRecordRepo, holdRepo, pinEncoder).reversePayment();
            interleaving.beforeExecution(2, () -> reversePayment.execute(new ReversePaymentCommand(
                    transactionId, new Id("op-002"), "OPERATOR", "chargeback", Id.generate())));

            assertThatThrownBy(() -> slowOperator.execute(new ReversePaymentCommand(
                    transactionId, new Id("op-001"), "OPERATOR", "chargeback", Id.generate())))
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertEquals(REVERSED, persisted(deposit.transactionId()).snapshot().state());
            assertEquals(4, persisted(deposit.transactionId()).snapshot().entries().size());
            assertTrue(AMOUNT_ZERO.equals(walletOf(CUST_ID_A).snapshot().balance().solde()));
        }

        @Test
        void an_unknown_transaction_cannot_be_reversed() {
            assertThatThrownBy(() -> reversePayment.execute(new ReversePaymentCommand(
                    new Id("trx-ghost"), new Id("op-001"), "OPERATOR", "dispute", Id.generate())))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("trx-ghost");
        }
    }

    @Nested
    class AuthorizationExpiry {

        private void expireAt(Instant now) {
            var useCases = UseCaseFixtures.build(
                    new DirectTransactionBoundary(), accountRepo, customerRepo, provider,
                    transactionRepo, historicRepo, ledgerRepository,
                    authorizationRecordRepo, holdRepo, pinEncoder);
            useCases.expireAuthorizations().execute(new ExpireAuthorizationsCommand(Id.generate(), now));
        }

        @Test
        void an_authorization_past_its_lifetime_fails_the_payment_it_authorized() {
            Id transactionId = authorized(TransactionType.CASH_IN, AMOUNT_5K);

            expireAt(Instant.now().plus(Duration.ofHours(1)));

            Transaction tx = persisted(transactionId.value());
            assertEquals(AUTHORIZATION_FAILED, tx.snapshot().state());
            TrxStateHistoric last = tx.history().getLast();
            assertEquals("AUTHORIZED", last.snapshot().oldState());
            assertEquals(TriggerSource.SYSTEM_JOB, last.snapshot().triggeredBy());
            assertThat(authorizationRecordRepo.findByTransactionId(transactionId))
                    .get().extracting(record -> record.snapshot().status())
                    .isEqualTo(AuthorizationRecord.AuthorizationStatus.EXPIRED);
        }

        @Test
        void an_authorization_still_alive_is_left_alone() {
            Id transactionId = authorized(TransactionType.CASH_IN, AMOUNT_5K);

            expireAt(Instant.now());

            assertEquals(TransactionState.AUTHORIZED, persisted(transactionId.value()).snapshot().state());
            assertThat(authorizationRecordRepo.findActiveByTransactionId(transactionId)).isPresent();
        }
    }
    private static final class InterleavingBoundary implements TransactionBoundary {

        private int executions;
        private int interleavedAt;
        private Runnable interleaved;

        private void beforeExecution(int execution, Runnable work) {
            this.interleavedAt = execution;
            this.interleaved = work;
        }

        @Override
        public <T> T execute(Supplier<T> work) {
            executions++;
            if (executions == interleavedAt) interleaved.run();
            return work.get();
        }
    }
}
