package com.geekersjoel237.koracore.payment.unit.application;

import com.geekersjoel237.koracore.payment.application.query.history.HistoryCriteria;
import com.geekersjoel237.koracore.payment.application.query.history.HistoryCursor;
import com.geekersjoel237.koracore.payment.application.query.history.HistoryFilter;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionHistoryQuery;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionKind;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionRow;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionSummary;
import com.geekersjoel237.koracore.payment.application.usecases.TransactionHistoryService;
import com.geekersjoel237.koracore.payment.domain.enums.CustomerStatus;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.payment.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.ports.in.TransactionHistoryQueryHandler;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryAccountRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryReadAccountRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryReadTransactionRepository;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.Pagination;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static com.geekersjoel237.koracore.payment.domain.model.state.TransactionState.AUTHORIZATION_FAILED;
import static com.geekersjoel237.koracore.payment.domain.model.state.TransactionState.CAPTURE_FAILED;
import static com.geekersjoel237.koracore.payment.domain.model.state.TransactionState.FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionHistoryServiceTest {

    private static final ZoneId DOUALA = ZoneId.of("Africa/Douala");
    private static final Id CUSTOMER = new Id("cust-001");
    private static final Id WALLET = new Id("account-00000001");
    private static final Id OTHER_WALLET = new Id("account-00000002");
    private static final Id FLOAT = new Id("account-float");
    private static final Amount AMOUNT_10K = Amount.of(BigDecimal.valueOf(10_000), "XAF");
    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

    private InMemoryReadTransactionRepository transactions;
    private TransactionHistoryQueryHandler history;

    @BeforeEach
    void setUp() {
        transactions = new InMemoryReadTransactionRepository();
        history = historyOf(WALLET, transactions);
    }

    private static TransactionHistoryQueryHandler historyOf(Id wallet, InMemoryReadTransactionRepository rows) {
        InMemoryAccountRepository accounts = new InMemoryAccountRepository();
        accounts.save(Account.createCustomerAccount(wallet, CUSTOMER));
        return new TransactionHistoryService(new InMemoryReadAccountRepository(accounts), rows, DOUALA);
    }

    private PageResult<TransactionSummary> read(HistoryFilter filter) {
        return history.execute(new TransactionHistoryQuery(CUSTOMER, filter, null, Pagination.firstPage()));
    }

    private TransactionSummary readOnly() {
        return read(HistoryFilter.empty()).content().getFirst();
    }

    private static TransactionRow row(TransactionType type, Id from, Id to, String state, PaymentMethod method,
                                      String counterpartPrefix, String counterpartNumber) {
        return new TransactionRow("trx-1", "TRX-1", type, state, AMOUNT_10K, method,
                from.value(), to.value(), counterpartPrefix, counterpartNumber, NOW);
    }

    private static TransactionRow deposit(String state) {
        return row(TransactionType.CASH_IN, FLOAT, WALLET, state, PaymentMethod.ORANGE_MONEY, null, null);
    }

    private static TransactionRow withdrawal() {
        return row(TransactionType.CASH_OUT, WALLET, FLOAT, "COMPLETED", PaymentMethod.MOBILE_MONEY, null, null);
    }

    private static TransactionRow transferToOther() {
        return row(TransactionType.P2P_TRANSFER, WALLET, OTHER_WALLET, "COMPLETED", PaymentMethod.WALLET,
                "+237", "699887766");
    }

    @Test
    void should_ask_the_query_port_for_the_wallet_of_the_customer() {
        read(HistoryFilter.empty());

        assertThat(transactions.lastWalletId()).isEqualTo(WALLET);
    }

    @Test
    void should_throw_when_the_customer_has_no_account() {
        assertThatThrownBy(() -> history.execute(new TransactionHistoryQuery(
                new Id("ghost"), HistoryFilter.empty(), null, Pagination.firstPage())))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void should_read_a_cash_in_as_a_deposit() {
        transactions.give(deposit("COMPLETED"));

        assertThat(readOnly().kind()).isEqualTo(TransactionKind.DEPOSIT);
    }

    @Test
    void should_read_a_cash_out_as_a_withdrawal() {
        transactions.give(withdrawal());

        assertThat(readOnly().kind()).isEqualTo(TransactionKind.WITHDRAWAL);
    }

    @Test
    void should_read_one_transfer_as_sent_by_the_sender_and_received_by_the_receiver() {
        transactions.give(transferToOther());
        assertThat(readOnly().kind()).isEqualTo(TransactionKind.TRANSFER_SENT);

        InMemoryReadTransactionRepository receiverRows = new InMemoryReadTransactionRepository();
        receiverRows.give(transferToOther());
        TransactionSummary asReceiver = historyOf(OTHER_WALLET, receiverRows)
                .execute(new TransactionHistoryQuery(CUSTOMER, HistoryFilter.empty(), null, Pagination.firstPage()))
                .content().getFirst();

        assertThat(asReceiver.kind()).isEqualTo(TransactionKind.TRANSFER_RECEIVED);
    }

    @Test
    void should_show_the_customer_status_instead_of_the_lifecycle_state() {
        transactions.give(deposit("SETTLEMENT_PENDING"));

        assertThat(readOnly().status()).isEqualTo(CustomerStatus.COMPLETED);
    }

    @Test
    void should_sign_the_amount_negative_when_money_leaves_the_wallet() {
        transactions.give(withdrawal());

        assertThat(readOnly().signedAmount()).isEqualByComparingTo("-10000");
    }

    @Test
    void should_sign_the_amount_positive_when_money_arrives_in_the_wallet() {
        transactions.give(deposit("COMPLETED"));

        assertThat(readOnly().signedAmount()).isEqualByComparingTo("10000");
    }

    @Test
    void should_name_the_channel_of_a_deposit() {
        transactions.give(deposit("COMPLETED"));

        assertThat(readOnly().channel()).isEqualTo(PaymentMethod.ORANGE_MONEY);
    }

    @Test
    void should_name_no_channel_for_a_transfer() {
        transactions.give(transferToOther());

        assertThat(readOnly().channel()).isNull();
    }

    @Test
    void should_mask_the_counterpart_of_a_transfer() {
        transactions.give(transferToOther());

        assertThat(readOnly().counterpart()).isEqualTo("+237699***766");
    }

    @Test
    void should_name_no_counterpart_for_a_deposit() {
        transactions.give(deposit("COMPLETED"));

        assertThat(readOnly().counterpart()).isNull();
    }

    // The counterparty account may since have been deleted: the join returns nulls.
    @Test
    void should_survive_a_transfer_whose_counterpart_could_not_be_joined() {
        transactions.give(row(TransactionType.P2P_TRANSFER, WALLET, OTHER_WALLET, "COMPLETED",
                PaymentMethod.WALLET, null, null));

        assertThat(readOnly().counterpart()).isNull();
    }

    @Test
    void should_carry_the_reference_the_amount_and_the_date() {
        transactions.give(deposit("COMPLETED"));

        TransactionSummary summary = readOnly();

        assertThat(summary.reference()).isEqualTo("TRX-1");
        assertThat(summary.amount()).isEqualTo(AMOUNT_10K);
        assertThat(summary.date()).isEqualTo(NOW);
    }

    @Test
    void should_point_the_cursor_at_the_item_itself() {
        transactions.give(deposit("COMPLETED"));

        assertThat(readOnly().cursor()).isEqualTo(new HistoryCursor(NOW, new Id("trx-1")));
    }

    @Test
    void should_ask_for_everything_when_nothing_is_filtered() {
        read(HistoryFilter.empty());

        HistoryCriteria criteria = transactions.lastCriteria();
        assertThat(criteria.states()).isEmpty();
        assertThat(criteria.kind()).isNull();
        assertThat(criteria.from()).isNull();
        assertThat(criteria.until()).isNull();
        assertThat(criteria.after()).isNull();
    }

    @Test
    void should_translate_a_status_into_the_states_behind_it() {
        read(new HistoryFilter(CustomerStatus.FAILED, null, null, null));

        assertThat(transactions.lastCriteria().states())
                .containsExactlyInAnyOrder(FAILED, AUTHORIZATION_FAILED, CAPTURE_FAILED);
    }

    @Test
    void should_pass_the_kind_through() {
        read(new HistoryFilter(null, TransactionKind.TRANSFER_RECEIVED, null, null));

        assertThat(transactions.lastCriteria().kind()).isEqualTo(TransactionKind.TRANSFER_RECEIVED);
    }

    // Douala is UTC+1, and the last day counts up to its own midnight.
    @Test
    void should_turn_calendar_days_into_a_half_open_window_in_the_local_zone() {
        read(new HistoryFilter(null, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));

        assertThat(transactions.lastCriteria().from()).isEqualTo(Instant.parse("2026-09-30T23:00:00Z"));
        assertThat(transactions.lastCriteria().until()).isEqualTo(Instant.parse("2026-10-31T23:00:00Z"));
    }

    @Test
    void should_refuse_a_window_that_ends_before_it_starts() {
        assertThatThrownBy(() -> new HistoryFilter(null, null, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_forward_the_cursor_and_the_limit() {
        HistoryCursor after = new HistoryCursor(NOW, new Id("trx-9"));
        Pagination limit = Pagination.of(5);

        history.execute(new TransactionHistoryQuery(CUSTOMER, HistoryFilter.empty(), after, limit));

        assertThat(transactions.lastCriteria().after()).isEqualTo(after);
        assertThat(transactions.lastPagination()).isEqualTo(limit);
    }

    @Test
    void should_keep_the_next_page_flag_of_the_query() {
        transactions.give(deposit("COMPLETED"), deposit("COMPLETED"));

        PageResult<TransactionSummary> page = history.execute(
                new TransactionHistoryQuery(CUSTOMER, HistoryFilter.empty(), null, Pagination.of(1)));

        assertThat(page.content()).hasSize(1);
        assertThat(page.hasNext()).isTrue();
    }
}
