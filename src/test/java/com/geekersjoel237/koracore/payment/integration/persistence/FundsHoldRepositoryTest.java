package com.geekersjoel237.koracore.payment.integration.persistence;

import com.geekersjoel237.koracore.payment.adapters.out.persistence.JpaAccountRepository;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.JpaFundsHoldRepository;
import com.geekersjoel237.koracore.payment.domain.enums.HoldStatus;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.domain.model.FundsHold;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.integration.persistence.AbstractRepositoryTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundsHoldRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private JpaFundsHoldRepository repository;

    @Autowired
    private JpaAccountRepository accounts;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager em;

    private final Id wallet = Id.generate();
    private final Id otherWallet = Id.generate();

    private static Amount xaf(long value) {
        return Amount.of(BigDecimal.valueOf(value).setScale(4), "XAF");
    }

    private void account(Id id) {
        jdbc.update("""
                INSERT INTO accounts (id, account_number, resource_type, resource_id,
                                      balance_amount, balance_currency, is_blocked, created_at, updated_at)
                VALUES (:id, :number, 'CUSTOMER_ACCOUNT', :id, 10000, 'XAF', false, now(), now())
                """, Map.of("id", id.value(), "number", "ACC-" + id.value()));
    }

    private Id transaction() {
        Id id = Id.generate();
        jdbc.update("""
                INSERT INTO transactions (id, transaction_number, from_account_id, to_account_id,
                                          state, type, payment_method, amount, currency,
                                          occurred_at, created_at, updated_at)
                VALUES (:id, :number, :wallet, :wallet, 'INITIALIZED', 'CASH_OUT', 'ORANGE_MONEY',
                        5000, 'XAF', now(), now(), now())
                """, Map.of("id", id.value(), "number", "TRX-" + id.value(), "wallet", wallet.value()));
        return id;
    }

    private FundsHold heldOn(Id account, Amount amount) {
        FundsHold hold = FundsHold.place(transaction(), account, amount);
        repository.save(hold);
        return hold;
    }

    private void flushAndForget() {
        em.flush();
        em.clear();
    }

    @BeforeEach
    void wallets() {
        account(wallet);
        account(otherWallet);
    }

    @Test
    void should_persist_and_return_an_active_hold_intact() {
        FundsHold hold = heldOn(wallet, xaf(5000));
        flushAndForget();

        assertThat(repository.findActiveByTransactionId(hold.snapshot().transactionId()))
                .map(FundsHold::snapshot)
                .contains(hold.snapshot());
    }

    @Test
    void should_no_longer_find_a_consumed_hold_as_active() {
        FundsHold hold = heldOn(wallet, xaf(5000));
        flushAndForget();

        FundsHold loaded = repository.findActiveByTransactionId(hold.snapshot().transactionId()).orElseThrow();
        loaded.consume();
        repository.save(loaded);
        flushAndForget();

        assertThat(repository.findActiveByTransactionId(hold.snapshot().transactionId())).isEmpty();
        String status = jdbc.queryForObject("SELECT status FROM funds_holds WHERE id = :id",
                Map.of("id", hold.snapshot().id().value()), String.class);
        assertThat(status).isEqualTo(HoldStatus.CONSUMED.name());
    }

    @Test
    void should_load_the_account_with_its_active_holds_reserved() {
        heldOn(wallet, xaf(3000));
        heldOn(wallet, xaf(2000));
        FundsHold consumed = heldOn(wallet, xaf(1000));
        consumed.consume();
        repository.save(consumed);
        heldOn(otherWallet, xaf(7000));
        flushAndForget();

        Account loaded = accounts.findById(wallet).orElseThrow();
        assertThat(loaded.snapshot().reserved().value()).isEqualByComparingTo("5000");
        assertThat(loaded.available().value()).isEqualByComparingTo("5000");
    }

    @Test
    void should_load_an_account_with_nothing_reserved() {
        assertThat(accounts.findById(wallet).orElseThrow().snapshot().reserved().value())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_refuse_a_second_active_hold_on_the_same_transaction() {
        FundsHold first = heldOn(wallet, xaf(5000));
        repository.save(FundsHold.place(first.snapshot().transactionId(), wallet, xaf(5000)));

        assertThatThrownBy(this::flushAndForget)
                .hasStackTraceContaining("uq_funds_holds_active_transaction");
    }
}
