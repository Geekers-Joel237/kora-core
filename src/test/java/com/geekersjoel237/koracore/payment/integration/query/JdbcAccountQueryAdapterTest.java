package com.geekersjoel237.koracore.payment.integration.query;

import com.geekersjoel237.koracore.payment.adapters.out.query.JdbcAccountQueryAdapter;
import com.geekersjoel237.koracore.payment.application.query.balance.BalanceResult;
import com.geekersjoel237.koracore.shared.integration.persistence.AbstractRepositoryTest;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resolving a customer to their wallet, and reading its balance.
 *
 * <p>Both statements filter on {@code resource_type} — a customer id and a provider id
 * live in the same column, and the pair is only unique together. Getting that wrong
 * would hand someone the float account, which is why it is asserted rather than assumed.
 */
class JdbcAccountQueryAdapterTest extends AbstractRepositoryTest {

    @Autowired
    private JdbcAccountQueryAdapter adapter;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private QueryFixtures given;
    private final Id customer = Id.generate();
    private final Id walletId = Id.generate();

    @BeforeEach
    void setUp() {
        given = new QueryFixtures(jdbc);
    }

    @Test
    void should_read_the_balance_of_a_customer_wallet() {
        given.wallet(walletId.value(), customer.value(), "12500.0000");

        BalanceResult balance = adapter.findBalance(customer).orElseThrow();

        assertThat(balance.accountId()).isEqualTo(walletId.value());
        assertThat(balance.accountNumber()).isEqualTo("ACC-" + walletId.value());
        assertThat(balance.balance()).isEqualTo(new Amount(new BigDecimal("12500.0000"), "XAF"));
    }

    @Test
    void should_find_the_wallet_id_of_a_customer() {
        given.wallet(walletId.value(), customer.value(), "0.0000");

        assertThat(adapter.findWalletId(customer)).contains(walletId);
    }

    @Test
    void should_find_nothing_for_an_unknown_customer() {
        assertThat(adapter.findBalance(Id.generate())).isEmpty();
        assertThat(adapter.findWalletId(Id.generate())).isEmpty();
    }

    @Test
    void should_not_hand_back_a_float_account_that_shares_the_resource_id() {
        given.account(Id.generate().value(), customer.value(), "FLOAT_ACCOUNT", "999999.0000");

        assertThat(adapter.findWalletId(customer)).isEmpty();
        assertThat(adapter.findBalance(customer)).isEmpty();
    }

    @Test
    void should_ignore_a_soft_deleted_wallet() {
        given.wallet(walletId.value(), customer.value(), "5000.0000");
        jdbc.update("UPDATE accounts SET deleted_at = now() WHERE id = :id",
                Map.of("id", walletId.value()));

        assertThat(adapter.findBalance(customer)).isEmpty();
    }
}
