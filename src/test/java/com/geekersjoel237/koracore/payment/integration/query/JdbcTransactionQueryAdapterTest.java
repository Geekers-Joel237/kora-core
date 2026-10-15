package com.geekersjoel237.koracore.payment.integration.query;

import com.geekersjoel237.koracore.payment.adapters.out.query.JdbcReadTransactionQueryAdapter;
import com.geekersjoel237.koracore.payment.application.query.history.HistoryCriteria;
import com.geekersjoel237.koracore.payment.application.query.history.HistoryCursor;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionKind;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionRow;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.payment.ports.out.repository.TransactionRepository;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.Pagination;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.integration.persistence.AbstractRepositoryTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

import static com.geekersjoel237.koracore.payment.domain.model.state.TransactionState.AUTHORIZATION_FAILED;
import static com.geekersjoel237.koracore.payment.domain.model.state.TransactionState.CAPTURE_FAILED;
import static org.assertj.core.api.Assertions.assertThat;

class JdbcTransactionQueryAdapterTest extends AbstractRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final HistoryCriteria EVERYTHING = HistoryCriteria.everything();

    @Autowired
    private JdbcReadTransactionQueryAdapter adapter;
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

    private PageResult<TransactionRow> read(HistoryCriteria criteria, int limit) {
        return adapter.findPage(wallet, criteria, Pagination.of(limit));
    }

    private PageResult<TransactionRow> readAll() {
        return read(EVERYTHING, 20);
    }

    private void deposit(String id, Instant at) {
        given.transaction(id, floatAccount.value(), wallet.value(), "CASH_IN", "COMPLETED", "1000.0000", at);
    }

    @Test
    void should_return_nothing_when_the_wallet_has_no_transaction() {
        assertThat(readAll().content()).isEmpty();
        assertThat(readAll().hasNext()).isFalse();
    }

    @Test
    void should_take_both_sides_of_the_movement() {
        deposit("in", T0);
        given.transaction("out", wallet.value(), floatAccount.value(),
                "CASH_OUT", "COMPLETED", "3000.0000", T0.plusSeconds(60));

        assertThat(readAll().content())
                .extracting(TransactionRow::transactionId)
                .containsExactly("out", "in");
    }

    @Test
    void should_ignore_a_transaction_between_two_other_accounts() {
        deposit("mine", T0);
        given.transaction("theirs", floatAccount.value(), otherWallet.value(),
                "CASH_IN", "COMPLETED", "10000.0000", T0);

        assertThat(readAll().content())
                .extracting(TransactionRow::transactionId)
                .containsExactly("mine");
    }

    @Test
    void should_ignore_a_soft_deleted_transaction() {
        deposit("gone", T0);
        jdbc.update("UPDATE transactions SET deleted_at = now() WHERE id = :id", Map.of("id", "gone"));

        assertThat(readAll().content()).isEmpty();
    }

    @Test
    void should_return_the_most_recent_first() {
        deposit("oldest", T0);
        deposit("newest", T0.plus(2, ChronoUnit.DAYS));
        deposit("middle", T0.plus(1, ChronoUnit.DAYS));

        assertThat(readAll().content())
                .extracting(TransactionRow::transactionId)
                .containsExactly("newest", "middle", "oldest");
    }

    @Test
    void should_stop_at_the_limit_and_announce_more() {
        for (int i = 0; i < 5; i++) deposit("t" + i, T0.plusSeconds(i * 60L));

        PageResult<TransactionRow> first = read(EVERYTHING, 2);

        assertThat(first.content()).extracting(TransactionRow::transactionId).containsExactly("t4", "t3");
        assertThat(first.hasNext()).isTrue();
    }

    @Test
    void should_continue_strictly_after_the_cursor() {
        for (int i = 0; i < 5; i++) deposit("t" + i, T0.plusSeconds(i * 60L));
        HistoryCursor afterT3 = new HistoryCursor(T0.plusSeconds(180), new Id("t3"));

        PageResult<TransactionRow> next = read(EVERYTHING.continuingAfter(afterT3), 2);

        assertThat(next.content()).extracting(TransactionRow::transactionId).containsExactly("t2", "t1");
        assertThat(next.hasNext()).isTrue();
    }

    @Test
    void should_announce_no_more_on_the_last_page() {
        for (int i = 0; i < 3; i++) deposit("t" + i, T0.plusSeconds(i * 60L));
        HistoryCursor afterT1 = new HistoryCursor(T0.plusSeconds(60), new Id("t1"));

        PageResult<TransactionRow> last = read(EVERYTHING.continuingAfter(afterT1), 2);

        assertThat(last.content()).extracting(TransactionRow::transactionId).containsExactly("t0");
        assertThat(last.hasNext()).isFalse();
    }

    // Two movements in the same instant: the id breaks the tie, so none is skipped or repeated.
    @Test
    void should_neither_skip_nor_repeat_rows_sharing_the_cursor_instant() {
        deposit("b", T0);
        deposit("a", T0);
        HistoryCursor afterB = new HistoryCursor(T0, new Id("b"));

        assertThat(read(EVERYTHING.continuingAfter(afterB), 20).content())
                .extracting(TransactionRow::transactionId)
                .containsExactly("a");
    }

    @Test
    void should_narrow_to_the_given_states() {
        deposit("done", T0);
        given.transaction("refused", floatAccount.value(), wallet.value(),
                "CASH_IN", "AUTHORIZATION_FAILED", "1000.0000", T0.plusSeconds(60));
        given.transaction("not-captured", floatAccount.value(), wallet.value(),
                "CASH_IN", "CAPTURE_FAILED", "1000.0000", T0.plusSeconds(120));

        HistoryCriteria failed = new HistoryCriteria(Set.of(AUTHORIZATION_FAILED, CAPTURE_FAILED),
                null, null, null, null);

        assertThat(read(failed, 20).content())
                .extracting(TransactionRow::transactionId)
                .containsExactly("not-captured", "refused");
    }

    @Test
    void should_narrow_to_each_kind_from_the_wallet_side() {
        deposit("deposit", T0);
        given.transaction("withdrawal", wallet.value(), floatAccount.value(),
                "CASH_OUT", "COMPLETED", "1000.0000", T0.plusSeconds(60));
        given.transaction("sent", wallet.value(), otherWallet.value(),
                "P2P_TRANSFER", "COMPLETED", "1000.0000", T0.plusSeconds(120));
        given.transaction("received", otherWallet.value(), wallet.value(),
                "P2P_TRANSFER", "COMPLETED", "1000.0000", T0.plusSeconds(180));

        assertThat(idsOfKind(TransactionKind.DEPOSIT)).containsExactly("deposit");
        assertThat(idsOfKind(TransactionKind.WITHDRAWAL)).containsExactly("withdrawal");
        assertThat(idsOfKind(TransactionKind.TRANSFER_SENT)).containsExactly("sent");
        assertThat(idsOfKind(TransactionKind.TRANSFER_RECEIVED)).containsExactly("received");
    }

    private java.util.List<String> idsOfKind(TransactionKind kind) {
        return read(new HistoryCriteria(Set.of(), kind, null, null, null), 20).content().stream()
                .map(TransactionRow::transactionId)
                .toList();
    }

    @Test
    void should_include_the_start_of_the_window_and_exclude_its_end() {
        deposit("before", T0.minusSeconds(1));
        deposit("at-start", T0);
        deposit("last-second", T0.plusSeconds(59));
        deposit("at-end", T0.plusSeconds(60));

        HistoryCriteria window = new HistoryCriteria(Set.of(), null, T0, T0.plusSeconds(60), null);

        assertThat(read(window, 20).content())
                .extracting(TransactionRow::transactionId)
                .containsExactly("last-second", "at-start");
    }

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
        deposit("in", T0);

        TransactionRow row = readAll().content().getFirst();

        assertThat(row.counterpartPhonePrefix()).isNull();
        assertThat(row.counterpartPhoneNumber()).isNull();
    }

    // The state history is not part of the customer view: it must not multiply the rows.
    @Test
    void should_return_one_row_per_transaction_whatever_its_state_history() {
        deposit("a", T0);
        deposit("b", T0.plusSeconds(60));
        given.stateChange("b", null, "INITIALIZED", T0.plusSeconds(60));
        given.stateChange("b", "INITIALIZED", "AUTHORIZED", T0.plusSeconds(61));
        given.stateChange("b", "AUTHORIZED", "CAPTURED", T0.plusSeconds(62));

        PageResult<TransactionRow> first = read(EVERYTHING, 1);

        assertThat(first.content()).extracting(TransactionRow::transactionId).containsExactly("b");
        assertThat(first.hasNext()).isTrue();
    }

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
        assertThat(row.state()).isEqualTo(tx.snapshot().state().name());
        assertThat(row.paymentMethod()).isEqualTo(PaymentMethod.ORANGE_MONEY);
        assertThat(row.amount()).isEqualTo(amount);
        assertThat(row.fromAccountId()).isEqualTo(floatAccount.value());
        assertThat(row.toAccountId()).isEqualTo(wallet.value());
    }
}
