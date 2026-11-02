package com.geekersjoel237.koracore.payment.integration.persistence;

import com.geekersjoel237.koracore.shared.integration.persistence.AbstractRepositoryTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppendOnlyLedgerDbTest extends AbstractRepositoryTest {

    private final SingleConnectionDataSource owner = ownerTransaction();
    private final JdbcTemplate jdbc = new JdbcTemplate(owner);

    private final String account = UUID.randomUUID().toString();
    private final String transaction = UUID.randomUUID().toString();
    private final String entry = UUID.randomUUID().toString();
    private final String historic = UUID.randomUUID().toString();

    @BeforeEach
    void aPostedTransaction() {
        jdbc.update("""
                INSERT INTO accounts (id, account_number, resource_type, resource_id,
                                      balance_amount, balance_currency, created_at, updated_at)
                VALUES (?, ?, 'CUSTOMER_ACCOUNT', ?, 0, 'XAF', now(), now())
                """, account, "ACC-" + account, account);
        jdbc.update("""
                INSERT INTO transactions (id, transaction_number, from_account_id, to_account_id,
                                          state, type, payment_method, amount, currency,
                                          occurred_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'COMPLETED', 'CASH_IN', 'ORANGE_MONEY', 5000, 'XAF', now(), now(), now())
                """, transaction, "TRX-" + transaction, account, account);
        insertEntry(entry, "5000");
        jdbc.update("""
                INSERT INTO trx_state_historics (id, transaction_id, old_state, new_state,
                                                 occurred_at, created_at, updated_at)
                VALUES (?, ?, NULL, 'INITIALIZED', now(), now(), now())
                """, historic, transaction);
    }

    @AfterEach
    void rollBack() throws SQLException {
        owner.getConnection().rollback();
        owner.destroy();
    }

    private void insertEntry(String id, String amount) {
        jdbc.update("""
                INSERT INTO ledger_entries (id, transaction_id, type, amount, currency, account_id,
                                            occurred_at, created_at, updated_at)
                VALUES (?, ?, 'CREDIT', CAST(? AS NUMERIC), 'XAF', ?, now(), now(), now())
                """, id, transaction, amount, account);
    }

    @Test
    void a_ledger_entry_cannot_be_updated() {
        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET amount = 1 WHERE id = ?", entry))
                .hasStackTraceContaining("append-only");
    }

    @Test
    void a_ledger_entry_cannot_be_soft_deleted() {
        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET deleted = true, deleted_at = now() WHERE id = ?", entry))
                .hasStackTraceContaining("append-only");
    }

    @Test
    void a_ledger_entry_cannot_be_deleted() {
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_entries WHERE id = ?", entry))
                .hasStackTraceContaining("append-only");
    }

    @Test
    void a_state_historic_cannot_be_updated() {
        assertThatThrownBy(() -> jdbc.update("UPDATE trx_state_historics SET new_state = 'COMPLETED' WHERE id = ?", historic))
                .hasStackTraceContaining("append-only");
    }

    @Test
    void a_state_historic_cannot_be_deleted() {
        assertThatThrownBy(() -> jdbc.update("DELETE FROM trx_state_historics WHERE id = ?", historic))
                .hasStackTraceContaining("append-only");
    }

    @Test
    void a_ledger_entry_cannot_carry_a_negative_amount() {
        assertThatThrownBy(() -> insertEntry(UUID.randomUUID().toString(), "-1"))
                .hasStackTraceContaining("ck_ledger_entries_amount");
    }

    @Test
    void a_ledger_entry_can_carry_a_zero_amount() {
        assertThatNoException().isThrownBy(() -> insertEntry(UUID.randomUUID().toString(), "0"));
    }
}
