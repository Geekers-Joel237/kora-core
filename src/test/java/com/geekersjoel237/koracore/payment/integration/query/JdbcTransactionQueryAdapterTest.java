package com.geekersjoel237.koracore.payment.integration.query;

import com.geekersjoel237.koracore.payment.adapters.out.query.JdbcTransactionQueryAdapter;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.Pagination;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionFilter;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionRow;
import com.geekersjoel237.koracore.payment.domain.enums.Direction;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.payment.ports.out.repository.TransactionRepository;
import com.geekersjoel237.koracore.shared.integration.persistence.AbstractRepositoryTest;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The read side against a real Postgres.
 *
 * <p>Everything the SQL owns is proved here and nowhere else: which rows belong to a
 * wallet, what each filter narrows, how a page is cut, and how the state-history join
 * folds many rows back into one transaction. The handler's unit test can no longer
 * cover any of it — that is the price of moving the query out of Java, and this is
 * where it is paid.
 */
class JdbcTransactionQueryAdapterTest extends AbstractRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private JdbcTransactionQueryAdapter adapter;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;
    @Autowired
    private TransactionRepository writeSide;
    @Autowired
    private EntityManager entityManager;

    private QueryFixtures given;
    private final Id wallet = Id.generate();
    private final Id otherWallet = Id.generate();
    private final Id floatAccount = Id.generate();

    @BeforeEach
    void setUp() {
        given = new QueryFixtures(jdbc);
        given.account(floatAccount.value(), Id.generate().value(), "FLOAT_ACCOUNT", "0.0000");
    }

    private PageResult<TransactionRow> read(TransactionFilter filter, int page, int size) {
        return adapter.findPage(wallet, filter, new Pagination(page, size));
    }

    private PageResult<TransactionRow> readAll() {
        return read(TransactionFilter.empty(), 0, 20);
    }

    // ── scoping ───────────────────────────────────────────────────────────────

    @Test
    void should_return_nothing_when_the_wallet_has_no_transaction() {
        assertThat(readAll().content()).isEmpty();
        assertThat(readAll().totalElements()).isZero();
    }

    @Test
    void should_take_both_sides_of_the_movement() {
        given.transaction("in", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "10000.0000", T0);
        given.transaction("out", wallet.value(), floatAccount.value(),
                "CASH_OUT", "COMPLETED", "3000.0000", T0.plusSeconds(60));

        assertThat(readAll().content())
                .extracting(TransactionRow::transactionId)
                .containsExactly("out", "in");
    }

    @Test
    void should_ignore_a_transaction_between_two_other_accounts() {
        given.transaction("mine", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "10000.0000", T0);
        given.transaction("theirs", floatAccount.value(), otherWallet.value(),
                "CASH_IN", "COMPLETED", "10000.0000", T0);

        assertThat(readAll().content())
                .extracting(TransactionRow::transactionId)
                .containsExactly("mine");
    }

    @Test
    void should_ignore_a_soft_deleted_transaction() {
        given.transaction("gone", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "10000.0000", T0);
        jdbc.update("UPDATE transactions SET deleted_at = now() WHERE id = :id",
                Map.of("id", "gone"));

        assertThat(readAll().content()).isEmpty();
    }

    // ── ordering, paging ──────────────────────────────────────────────────────

    @Test
    void should_return_the_most_recent_first() {
        given.transaction("oldest", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "1000.0000", T0);
        given.transaction("newest", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "1000.0000", T0.plus(2, ChronoUnit.DAYS));
        given.transaction("middle", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "1000.0000", T0.plus(1, ChronoUnit.DAYS));

        assertThat(readAll().content())
                .extracting(TransactionRow::transactionId)
                .containsExactly("newest", "middle", "oldest");
    }

    @Test
    void should_cut_a_page_and_still_count_the_whole_set() {
        for (int i = 0; i < 5; i++) {
            given.transaction("t" + i, floatAccount.value(), wallet.value(),
                    "CASH_IN", "COMPLETED", "1000.0000", T0.plusSeconds(i * 60L));
        }

        PageResult<TransactionRow> secondPage = read(TransactionFilter.empty(), 1, 2);

        assertThat(secondPage.content())
                .extracting(TransactionRow::transactionId)
                .containsExactly("t2", "t1");
        assertThat(secondPage.totalElements()).isEqualTo(5);
        assertThat(secondPage.page()).isEqualTo(1);
    }

    @Test
    void should_return_an_empty_page_past_the_end() {
        given.transaction("only", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "1000.0000", T0);

        PageResult<TransactionRow> beyond = read(TransactionFilter.empty(), 9, 20);

        assertThat(beyond.content()).isEmpty();
        assertThat(beyond.totalElements()).isEqualTo(1);
    }

    // ── filters ───────────────────────────────────────────────────────────────

    @Test
    void should_narrow_to_outbound() {
        given.transaction("in", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "10000.0000", T0);
        given.transaction("out", wallet.value(), floatAccount.value(),
                "CASH_OUT", "COMPLETED", "3000.0000", T0.plusSeconds(60));

        PageResult<TransactionRow> outbound = read(
                new TransactionFilter(null, null, null, null, Direction.OUTBOUND), 0, 20);

        assertThat(outbound.content())
                .extracting(TransactionRow::transactionId)
                .containsExactly("out");
        assertThat(outbound.totalElements()).isEqualTo(1);
    }

    @Test
    void should_narrow_to_inbound() {
        given.transaction("in", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "10000.0000", T0);
        given.transaction("out", wallet.value(), floatAccount.value(),
                "CASH_OUT", "COMPLETED", "3000.0000", T0.plusSeconds(60));

        assertThat(read(new TransactionFilter(null, null, null, null, Direction.INBOUND), 0, 20)
                .content())
                .extracting(TransactionRow::transactionId)
                .containsExactly("in");
    }

    @Test
    void should_narrow_by_type() {
        given.transaction("cash-in", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "10000.0000", T0);
        given.transaction("p2p", wallet.value(), otherWallet.value(),
                "P2P_TRANSFER", "COMPLETED", "2000.0000", T0.plusSeconds(60));

        assertThat(read(new TransactionFilter(TransactionType.P2P_TRANSFER, null, null, null, null), 0, 20)
                .content())
                .extracting(TransactionRow::transactionId)
                .containsExactly("p2p");
    }

    @Test
    void should_narrow_by_state() {
        given.transaction("done", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "10000.0000", T0);
        given.transaction("failed", floatAccount.value(), wallet.value(),
                "CASH_IN", "FAILED", "10000.0000", T0.plusSeconds(60));

        assertThat(read(new TransactionFilter(null, "FAILED", null, null, null), 0, 20)
                .content())
                .extracting(TransactionRow::transactionId)
                .containsExactly("failed");
    }

    @Test
    void should_narrow_by_date_range_inclusively() {
        given.transaction("before", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "1000.0000", T0.minusSeconds(1));
        given.transaction("lower-bound", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "1000.0000", T0);
        given.transaction("upper-bound", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "1000.0000", T0.plusSeconds(60));
        given.transaction("after", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "1000.0000", T0.plusSeconds(61));

        PageResult<TransactionRow> window = read(
                new TransactionFilter(null, null, T0, T0.plusSeconds(60), null), 0, 20);

        assertThat(window.content())
                .extracting(TransactionRow::transactionId)
                .containsExactly("upper-bound", "lower-bound");
    }

    @Test
    void should_combine_filters() {
        given.transaction("kept", wallet.value(), otherWallet.value(),
                "P2P_TRANSFER", "COMPLETED", "2000.0000", T0);
        given.transaction("wrong-direction", otherWallet.value(), wallet.value(),
                "P2P_TRANSFER", "COMPLETED", "2000.0000", T0);
        given.transaction("wrong-type", wallet.value(), floatAccount.value(),
                "CASH_OUT", "COMPLETED", "2000.0000", T0);

        assertThat(read(new TransactionFilter(TransactionType.P2P_TRANSFER, "COMPLETED",
                null, null, Direction.OUTBOUND), 0, 20).content())
                .extracting(TransactionRow::transactionId)
                .containsExactly("kept");
    }

    // ── the joins ─────────────────────────────────────────────────────────────

    @Test
    void should_carry_the_counterpart_phone_of_the_other_side() {
        Id counterpartCustomer = Id.generate();
        given.customer(counterpartCustomer.value(), "+237", "699887766");
        given.wallet(otherWallet.value(), counterpartCustomer.value(), "0.0000");
        given.transaction("p2p", wallet.value(), otherWallet.value(),
                "P2P_TRANSFER", "COMPLETED", "2000.0000", T0);

        TransactionRow row = readAll().content().getFirst();

        assertThat(row.counterpartPhonePrefix()).isEqualTo("+237");
        assertThat(row.counterpartPhoneNumber()).isEqualTo("699887766");
    }

    @Test
    void should_leave_the_counterpart_null_when_the_other_side_is_the_float_account() {
        given.transaction("in", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "10000.0000", T0);

        TransactionRow row = readAll().content().getFirst();

        assertThat(row.counterpartPhonePrefix()).isNull();
        assertThat(row.counterpartPhoneNumber()).isNull();
    }

    @Test
    void should_fold_the_state_history_into_one_row_in_order() {
        given.transaction("t", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "10000.0000", T0);
        given.stateChange("t", null, "INITIALIZED", T0);
        given.stateChange("t", "INITIALIZED", "AUTHORIZED", T0.plusSeconds(1));
        given.stateChange("t", "AUTHORIZED", "CAPTURED", T0.plusSeconds(2));

        List<TransactionRow> rows = readAll().content();

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().stateHistory())
                .extracting("newState")
                .containsExactly("INITIALIZED", "AUTHORIZED", "CAPTURED");
        assertThat(rows.getFirst().stateHistory().getFirst().oldState()).isNull();
    }

    @Test
    void should_not_let_the_history_join_inflate_the_page_count() {
        given.transaction("a", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "1000.0000", T0);
        given.transaction("b", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "1000.0000", T0.plusSeconds(60));
        given.stateChange("a", null, "INITIALIZED", T0);
        given.stateChange("a", "INITIALIZED", "AUTHORIZED", T0.plusSeconds(1));
        given.stateChange("b", null, "INITIALIZED", T0.plusSeconds(60));

        PageResult<TransactionRow> firstPage = read(TransactionFilter.empty(), 0, 1);

        assertThat(firstPage.content()).hasSize(1);
        assertThat(firstPage.totalElements()).isEqualTo(2);
    }

    @Test
    void should_give_an_empty_history_when_none_was_recorded() {
        given.transaction("t", floatAccount.value(), wallet.value(),
                "CASH_IN", "COMPLETED", "10000.0000", T0);

        assertThat(readAll().content().getFirst().stateHistory()).isEmpty();
    }

    // ── the seam ──────────────────────────────────────────────────────────────

    /**
     * The read side and the write side are two implementations over one schema. Every
     * other test here inserts its own rows, so this is the one that would catch them
     * drifting apart.
     */
    @Test
    void should_read_back_what_the_write_side_actually_stored() {
        Amount amount = new Amount(new BigDecimal("7500.0000"), "XAF");
        Transaction tx = Transaction.create(Id.generate(), floatAccount, wallet,
                TransactionType.CASH_IN, PaymentMethod.ORANGE_MONEY, amount);
        tx.recordDoubleEntry(amount, floatAccount, wallet);
        writeSide.save(tx);
        entityManager.flush();

        TransactionRow row = readAll().content().getFirst();

        assertThat(row.transactionId()).isEqualTo(tx.snapshot().transactionId().value());
        assertThat(row.transactionNumber()).isEqualTo(tx.snapshot().transactionNumber());
        assertThat(row.type()).isEqualTo(TransactionType.CASH_IN);
        assertThat(row.paymentMethod()).isEqualTo(PaymentMethod.ORANGE_MONEY);
        assertThat(row.amount()).isEqualTo(amount);
        assertThat(row.fromAccountId()).isEqualTo(floatAccount.value());
        assertThat(row.toAccountId()).isEqualTo(wallet.value());
    }
}
