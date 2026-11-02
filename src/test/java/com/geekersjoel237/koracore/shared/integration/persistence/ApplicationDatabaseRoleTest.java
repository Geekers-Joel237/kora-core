package com.geekersjoel237.koracore.shared.integration.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationDatabaseRoleTest extends AbstractRepositoryTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void the_application_does_not_connect_as_a_superuser() {
        Boolean superuser = jdbc.queryForObject(
                "SELECT rolsuper FROM pg_roles WHERE rolname = current_user", Boolean.class);

        assertThat(superuser).describedAs("connected as %s",
                jdbc.queryForObject("SELECT current_user", String.class)).isFalse();
    }

    @Test
    void the_application_cannot_create_a_table() {
        assertThatThrownBy(() -> jdbc.execute("CREATE TABLE l11_probe (id INT)"))
                .hasStackTraceContaining("permission denied");
    }

    @Test
    void the_application_cannot_drop_a_table() {
        assertThatThrownBy(() -> jdbc.execute("DROP TABLE refresh_tokens"))
                .hasStackTraceContaining("must be owner");
    }

    @Test
    void the_application_cannot_alter_a_table() {
        assertThatThrownBy(() -> jdbc.execute("ALTER TABLE accounts ADD COLUMN l11_probe INT"))
                .hasStackTraceContaining("must be owner");
    }

    @Test
    void the_application_cannot_truncate_a_table() {
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE ledger_entries"))
                .hasStackTraceContaining("permission denied");
    }

    @Test
    void the_application_cannot_disable_the_append_only_trigger() {
        assertThatThrownBy(() -> jdbc.execute(
                "ALTER TABLE ledger_entries DISABLE TRIGGER ledger_entries_append_only"))
                .hasStackTraceContaining("must be owner");
    }

    @Test
    void the_application_has_no_right_to_update_or_delete_ledger_rows() {
        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET amount = amount WHERE false"))
                .hasStackTraceContaining("permission denied");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM trx_state_historics WHERE false"))
                .hasStackTraceContaining("permission denied");
    }

    @Test
    void the_application_can_still_read_and_write_its_data() {
        assertThatNoException().isThrownBy(() -> {
            jdbc.queryForObject("SELECT COUNT(*) FROM accounts", Long.class);
            jdbc.update("UPDATE accounts SET balance_amount = balance_amount WHERE false");
        });
    }
}
