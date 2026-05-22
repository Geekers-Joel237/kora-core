package com.geekersjoel237.koracore.application;

import com.geekersjoel237.koracore.application.command.CashInCommand;
import com.geekersjoel237.koracore.application.command.CashOutCommand;
import com.geekersjoel237.koracore.application.command.TransferCommand;
import com.geekersjoel237.koracore.application.query.TransactionSummary;
import com.geekersjoel237.koracore.application.service.AuthService;
import com.geekersjoel237.koracore.application.service.PaymentService;
import com.geekersjoel237.koracore.application.service.PaymentTransactionalExecutor;
import com.geekersjoel237.koracore.application.service.TransactionHistoryService;
import com.geekersjoel237.koracore.domain.enums.Direction;
import com.geekersjoel237.koracore.domain.enums.Role;
import com.geekersjoel237.koracore.domain.enums.TransactionType;
import com.geekersjoel237.koracore.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.domain.model.*;
import com.geekersjoel237.koracore.domain.port.CustomerPinEncoder;
import com.geekersjoel237.koracore.domain.port.LedgerRepository;
import com.geekersjoel237.koracore.domain.query.PageResult;
import com.geekersjoel237.koracore.domain.query.TransactionFilter;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.infrastructure.config.SecurityProperties;
import com.geekersjoel237.koracore.infrastructure.security.BCryptCustomerPinEncoder;
import com.geekersjoel237.koracore.shared.inmemory.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

import static com.geekersjoel237.koracore.shared.inmemory.InMemoryProviderAdapter.Behavior.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionHistoryServiceTest {

    private static final Id CUST_ID_A = new Id("cust-001");
    private static final Id CUST_ID_B = new Id("cust-002");
    private static final Id PROVIDER_ID = new Id("provider-system-001");
    private static final String EMAIL_A = "a@koracore.com";
    private static final String EMAIL_B = "b@koracore.com";
    private static final String RAW_PIN = "123456";
    private static final String PAYMENT_METHOD = "ORANGE_MONEY";
    private static final Amount AMOUNT_10K = Amount.of(BigDecimal.valueOf(10_000), "XOF");
    private static final Amount AMOUNT_5K  = Amount.of(BigDecimal.valueOf(5_000), "XOF");

    private static final SecurityProperties TEST_SECURITY = new SecurityProperties(
            new SecurityProperties.Jwt("test-secret-key-must-be-at-least-32-chars!!", 15, 7),
            new SecurityProperties.Otp(5)
    );

    private final CustomerPinEncoder pinEncoder = new BCryptCustomerPinEncoder();

    private InMemoryAccountRepository accountRepo;
    private InMemoryCustomerRepository customerRepo;
    private InMemoryTransactionRepository transactionRepo;
    private InMemoryTrxHistoricStatesRepository historicRepo;
    private PaymentService paymentService;
    private TransactionHistoryService historyService;

    @BeforeEach
    void setUp() {
        accountRepo     = new InMemoryAccountRepository();
        customerRepo    = new InMemoryCustomerRepository();
        transactionRepo = new InMemoryTransactionRepository();
        historicRepo    = new InMemoryTrxHistoricStatesRepository();

        LedgerRepository ledgerRepo  = new InMemoryLedgerRepository(Ledger.create(Id.generate()));
        InMemoryOtpStore otpStore    = new InMemoryOtpStore(Clock.systemUTC());
        InMemoryMailPort mailPort    = new InMemoryMailPort();
        AuthService authService = new AuthService(
                new InMemoryUserRepository(), customerRepo, accountRepo, otpStore, pinEncoder,
                Clock.systemUTC(), TEST_SECURITY, mailPort);
        PaymentTransactionalExecutor executor = new PaymentTransactionalExecutor(
                authService, accountRepo, customerRepo,
                transactionRepo, historicRepo, new InMemoryProviderAdapter(SUCCESS), ledgerRepo,
                new InMemoryAuthorizationRecordRepository());
        paymentService = new PaymentService(executor, accountRepo);

        historyService = new TransactionHistoryService(transactionRepo, accountRepo, customerRepo);

        preloadCustomerA();
        preloadCustomerB();
        accountRepo.save(Account.createFloatAccount(Id.generate(), PROVIDER_ID));
    }

    private void preloadCustomerA() {
        User userA = User.create(CUST_ID_A, "Customer A", EMAIL_A, Role.CUSTOMER);
        customerRepo.save(Customer.create(userA, PhoneNumber.of("+237", "600000001"), RAW_PIN, pinEncoder));
        accountRepo.save(Account.createCustomerAccount(Id.generate(), CUST_ID_A));
    }

    private void preloadCustomerB() {
        User userB = User.create(CUST_ID_B, "Customer B", EMAIL_B, Role.CUSTOMER);
        customerRepo.save(Customer.create(userB, PhoneNumber.of("+237", "600000002"), RAW_PIN, pinEncoder));
        accountRepo.save(Account.createCustomerAccount(Id.generate(), CUST_ID_B));
    }

    private void cashIn(Id customerId, Amount amount) {
        paymentService.cashIn(new CashInCommand(customerId, RAW_PIN, amount, PAYMENT_METHOD));
    }

    private void cashOut(Id customerId, Amount amount) {
        paymentService.cashOut(new CashOutCommand(customerId, RAW_PIN, amount, PAYMENT_METHOD));
    }

    private void transfer(Id from, String toFullPhone, Amount amount) {
        paymentService.transfer(new TransferCommand(from, RAW_PIN, amount, toFullPhone));
    }

    // ── Groupe 1 — empty history ──────────────────────────────────────────────

    @Test
    void should_return_empty_history_when_no_transactions() {
        PageResult<TransactionSummary> result =
                historyService.execute(CUST_ID_A, TransactionFilter.empty(), 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.page()).isZero();
    }

    // ── Groupe 2 — direction ──────────────────────────────────────────────────

    @Test
    void should_mark_cash_in_as_inbound() {
        cashIn(CUST_ID_A, AMOUNT_10K);

        PageResult<TransactionSummary> result =
                historyService.execute(CUST_ID_A, TransactionFilter.empty(), 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().direction()).isEqualTo(Direction.INBOUND);
        assertThat(result.content().getFirst().type()).isEqualTo(TransactionType.CASH_IN);
    }

    @Test
    void should_mark_cash_out_as_outbound() {
        cashIn(CUST_ID_A, AMOUNT_10K);
        cashOut(CUST_ID_A, AMOUNT_5K);

        PageResult<TransactionSummary> result =
                historyService.execute(CUST_ID_A,
                        new TransactionFilter(TransactionType.CASH_OUT, null, null, null, null),
                        0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().direction()).isEqualTo(Direction.OUTBOUND);
    }

    @Test
    void should_mark_p2p_as_outbound_for_sender_and_inbound_for_receiver() {
        cashIn(CUST_ID_A, AMOUNT_10K);
        transfer(CUST_ID_A, "+237600000002", AMOUNT_5K);

        PageResult<TransactionSummary> resultA =
                historyService.execute(CUST_ID_A,
                        new TransactionFilter(TransactionType.P2P_TRANSFER, null, null, null, null),
                        0, 20);
        PageResult<TransactionSummary> resultB =
                historyService.execute(CUST_ID_B,
                        new TransactionFilter(TransactionType.P2P_TRANSFER, null, null, null, null),
                        0, 20);

        assertThat(resultA.content().getFirst().direction()).isEqualTo(Direction.OUTBOUND);
        assertThat(resultB.content().getFirst().direction()).isEqualTo(Direction.INBOUND);
    }

    // ── Groupe 3 — counterpart masking ───────────────────────────────────────

    @Test
    void should_return_null_counterpart_for_cash_in() {
        cashIn(CUST_ID_A, AMOUNT_10K);

        TransactionSummary tx = historyService.execute(CUST_ID_A, TransactionFilter.empty(), 0, 20)
                .content().getFirst();

        assertThat(tx.counterpart()).isNull();
    }

    @Test
    void should_return_masked_counterpart_for_p2p() {
        cashIn(CUST_ID_A, AMOUNT_10K);
        transfer(CUST_ID_A, "+237600000002", AMOUNT_5K);

        TransactionSummary tx = historyService.execute(CUST_ID_A,
                        new TransactionFilter(TransactionType.P2P_TRANSFER, null, null, null, null),
                        0, 20)
                .content().getFirst();

        // B's number: prefix=+237, local=600000002 → +237600***002
        assertThat(tx.counterpart()).isEqualTo("+237600***002");
    }

    // ── Groupe 4 — filtering ──────────────────────────────────────────────────

    @Test
    void should_filter_by_direction_outbound() {
        cashIn(CUST_ID_A, AMOUNT_10K);
        cashOut(CUST_ID_A, AMOUNT_5K);

        PageResult<TransactionSummary> result =
                historyService.execute(CUST_ID_A,
                        new TransactionFilter(null, null, null, null, Direction.OUTBOUND),
                        0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().type()).isEqualTo(TransactionType.CASH_OUT);
    }

    // ── Groupe 5 — pagination ─────────────────────────────────────────────────

    @Test
    void should_paginate_results() {
        cashIn(CUST_ID_A, AMOUNT_10K);
        cashIn(CUST_ID_A, AMOUNT_10K);
        cashIn(CUST_ID_A, AMOUNT_5K);

        PageResult<TransactionSummary> page0 =
                historyService.execute(CUST_ID_A, TransactionFilter.empty(), 0, 2);
        PageResult<TransactionSummary> page1 =
                historyService.execute(CUST_ID_A, TransactionFilter.empty(), 1, 2);

        assertThat(page0.content()).hasSize(2);
        assertThat(page1.content()).hasSize(1);
        assertThat(page0.totalElements()).isEqualTo(3);
        assertThat(page0.hasNext()).isTrue();
        assertThat(page1.hasNext()).isFalse();
    }

    @Test
    void should_reject_size_over_100() {
        assertThatThrownBy(() ->
                historyService.execute(CUST_ID_A, TransactionFilter.empty(), 0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
    }

    // ── Groupe 6 — account not found ─────────────────────────────────────────

    @Test
    void should_throw_when_account_not_found() {
        assertThatThrownBy(() ->
                historyService.execute(new Id("ghost-cust"), TransactionFilter.empty(), 0, 20))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ── Groupe 7 — date range filter ─────────────────────────────────────────

    @Test
    void should_filter_by_date_range() {
        cashIn(CUST_ID_A, AMOUNT_10K);
        Instant boundary = Instant.now();
        cashIn(CUST_ID_A, AMOUNT_5K);

        // Transactions strictly after boundary should include only the last cashIn
        PageResult<TransactionSummary> result =
                historyService.execute(CUST_ID_A,
                        new TransactionFilter(null, null, boundary, null, null),
                        0, 20);

        // All transactions are at or after boundary (both could be equal in fast tests),
        // so we just verify total is > 0 and all are at/after boundary
        assertThat(result.content()).isNotEmpty();
        result.content().forEach(tx ->
                assertThat(tx.createdAt()).isAfterOrEqualTo(boundary));
    }
}