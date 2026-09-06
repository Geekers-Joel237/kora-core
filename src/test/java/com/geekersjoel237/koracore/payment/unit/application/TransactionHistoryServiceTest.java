package com.geekersjoel237.koracore.payment.unit.application;

import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryAccountQueryPort;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryAccountRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryTransactionQueryPort;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.Pagination;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionFilter;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionHistoryQuery;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionRow;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionSummary;
import com.geekersjoel237.koracore.payment.application.usecases.TransactionHistoryService;
import com.geekersjoel237.koracore.payment.domain.enums.Direction;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.payment.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.ports.in.TransactionHistoryQueryHandler;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the handler still decides, now that the fetching is SQL.
 *
 * <p>Two things: which way the money went from the reader's point of view, and how a
 * counterparty's number is shown. Both are rules about what a customer sees, and both
 * would be wrong in a way no filter test would catch — a direction flipped for the
 * receiver, a raw phone number rendered in a client.
 *
 * <p>Scoping, filtering, paging and the joins moved with the query, and are proved in
 * {@code JdbcTransactionQueryAdapterTest} against a real Postgres. Reimplementing them
 * over a map here would test a second implementation nobody ships.
 */
class TransactionHistoryServiceTest {

    private static final Id CUSTOMER = new Id("cust-001");
    private static final Id WALLET = new Id("account-00000001");
    private static final Id COUNTERPART_WALLET = new Id("account-00000002");
    private static final Amount AMOUNT_10K = Amount.of(BigDecimal.valueOf(10_000), "XAF");
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private InMemoryAccountRepository accounts;
    private InMemoryTransactionQueryPort transactions;
    private TransactionHistoryQueryHandler history;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccountRepository();
        accounts.save(Account.createCustomerAccount(WALLET, CUSTOMER));

        transactions = new InMemoryTransactionQueryPort();
        history = new TransactionHistoryService(
                new InMemoryAccountQueryPort(accounts), transactions);
    }

    private PageResult<TransactionSummary> read() {
        return history.execute(
                new TransactionHistoryQuery(CUSTOMER, TransactionFilter.empty(), new Pagination(0, 20)));
    }

    private static TransactionRow row(TransactionType type, Id from, Id to,
                                      String counterpartPrefix, String counterpartNumber) {
        return new TransactionRow("trx-1", "TRX-1", type, "COMPLETED", AMOUNT_10K,
                PaymentMethod.ORANGE_MONEY, from.value(), to.value(),
                counterpartPrefix, counterpartNumber, NOW, List.of());
    }

    // ── resolving the wallet ──────────────────────────────────────────────────

    @Test
    void should_ask_the_query_port_for_the_wallet_of_the_customer() {
        read();

        assertThat(transactions.lastWalletId()).isEqualTo(WALLET);
    }

    @Test
    void should_throw_when_the_customer_has_no_account() {
        assertThatThrownBy(() -> history.execute(new TransactionHistoryQuery(
                new Id("ghost"), TransactionFilter.empty(), new Pagination(0, 20))))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void should_pass_the_filter_through_untouched() {
        TransactionFilter filter =
                new TransactionFilter(TransactionType.CASH_IN, "FAILED", null, null, Direction.INBOUND);

        history.execute(new TransactionHistoryQuery(CUSTOMER, filter, new Pagination(0, 20)));

        assertThat(transactions.lastFilter()).isEqualTo(filter);
    }

    @Test
    void should_return_an_empty_page_when_there_is_nothing_to_show() {
        PageResult<TransactionSummary> page = read();

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    // ── direction ─────────────────────────────────────────────────────────────

    @Test
    void should_mark_money_arriving_in_the_wallet_as_inbound() {
        transactions.give(row(TransactionType.CASH_IN, COUNTERPART_WALLET, WALLET, null, null));

        assertThat(read().content().getFirst().direction()).isEqualTo(Direction.INBOUND);
    }

    @Test
    void should_mark_money_leaving_the_wallet_as_outbound() {
        transactions.give(row(TransactionType.CASH_OUT, WALLET, COUNTERPART_WALLET, null, null));

        assertThat(read().content().getFirst().direction()).isEqualTo(Direction.OUTBOUND);
    }

    /**
     * The same transfer read by its two parties. One row, two directions — which is
     * exactly why direction cannot be a column.
     */
    @Test
    void should_read_one_transfer_as_outbound_for_the_sender_and_inbound_for_the_receiver() {
        TransactionRow transfer = row(TransactionType.P2P_TRANSFER,
                WALLET, COUNTERPART_WALLET, "+237", "699887766");

        transactions.give(transfer);
        assertThat(read().content().getFirst().direction()).isEqualTo(Direction.OUTBOUND);

        InMemoryAccountRepository receiverAccounts = new InMemoryAccountRepository();
        receiverAccounts.save(Account.createCustomerAccount(COUNTERPART_WALLET, CUSTOMER));
        InMemoryTransactionQueryPort receiverRows = new InMemoryTransactionQueryPort();
        receiverRows.give(transfer);

        TransactionHistoryQueryHandler asReceiver = new TransactionHistoryService(
                new InMemoryAccountQueryPort(receiverAccounts), receiverRows);

        assertThat(asReceiver.execute(new TransactionHistoryQuery(
                CUSTOMER, TransactionFilter.empty(), new Pagination(0, 20)))
                .content().getFirst().direction()).isEqualTo(Direction.INBOUND);
    }

    // ── counterparty ──────────────────────────────────────────────────────────

    @Test
    void should_mask_the_counterpart_of_a_transfer() {
        transactions.give(row(TransactionType.P2P_TRANSFER,
                WALLET, COUNTERPART_WALLET, "+237", "699887766"));

        String counterpart = read().content().getFirst().counterpart();

        assertThat(counterpart).isNotNull();
        assertThat(counterpart).doesNotContain("699887766");
    }

    @Test
    void should_leave_the_counterpart_null_on_a_cash_in() {
        transactions.give(row(TransactionType.CASH_IN, COUNTERPART_WALLET, WALLET, null, null));

        assertThat(read().content().getFirst().counterpart()).isNull();
    }

    @Test
    void should_leave_the_counterpart_null_on_a_cash_out() {
        transactions.give(row(TransactionType.CASH_OUT, WALLET, COUNTERPART_WALLET, null, null));

        assertThat(read().content().getFirst().counterpart()).isNull();
    }

    /**
     * A transfer whose counterparty account was since deleted still has to render. The
     * join returns nulls, and nothing may dereference them.
     */
    @Test
    void should_survive_a_transfer_whose_counterpart_could_not_be_joined() {
        transactions.give(row(TransactionType.P2P_TRANSFER,
                WALLET, COUNTERPART_WALLET, null, null));

        assertThat(read().content().getFirst().counterpart()).isNull();
    }

    // ── the rest of the mapping ───────────────────────────────────────────────

    @Test
    void should_carry_every_other_field_across_unchanged() {
        transactions.give(row(TransactionType.CASH_IN, COUNTERPART_WALLET, WALLET, null, null));

        TransactionSummary summary = read().content().getFirst();

        assertThat(summary.transactionId()).isEqualTo("trx-1");
        assertThat(summary.transactionNumber()).isEqualTo("TRX-1");
        assertThat(summary.type()).isEqualTo(TransactionType.CASH_IN);
        assertThat(summary.state()).isEqualTo("COMPLETED");
        assertThat(summary.amount()).isEqualTo(AMOUNT_10K);
        assertThat(summary.paymentMethod()).isEqualTo(PaymentMethod.ORANGE_MONEY);
        assertThat(summary.createdAt()).isEqualTo(NOW);
    }

    @Test
    void should_keep_the_state_history_the_query_returned() {
        transactions.give(new TransactionRow("trx-1", "TRX-1", TransactionType.CASH_IN,
                "COMPLETED", AMOUNT_10K, PaymentMethod.ORANGE_MONEY,
                COUNTERPART_WALLET.value(), WALLET.value(), null, null, NOW,
                List.of(new TransactionSummary.StateEntry(null, "INITIALIZED", NOW),
                        new TransactionSummary.StateEntry("INITIALIZED", "AUTHORIZED", NOW))));

        assertThat(read().content().getFirst().stateHistory())
                .extracting(TransactionSummary.StateEntry::newState)
                .containsExactly("INITIALIZED", "AUTHORIZED");
    }

    @Test
    void should_forward_the_pagination_to_the_query_port() {
        Pagination pagination = new Pagination(2, 5);

        history.execute(new TransactionHistoryQuery(CUSTOMER, TransactionFilter.empty(), pagination));

        assertThat(transactions.lastPagination()).isEqualTo(pagination);
    }

    @Test
    void should_keep_the_paging_metadata_of_the_query() {
        transactions.give(row(TransactionType.CASH_IN, COUNTERPART_WALLET, WALLET, null, null));

        PageResult<TransactionSummary> page = history.execute(
                new TransactionHistoryQuery(CUSTOMER, TransactionFilter.empty(), new Pagination(2, 5)));

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(5);
    }
}
