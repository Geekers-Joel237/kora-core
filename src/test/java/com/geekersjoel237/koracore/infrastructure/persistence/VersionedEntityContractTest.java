package com.geekersjoel237.koracore.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the @Version column exists on mutable entities and
 * is intentionally absent from immutable (append-only) entities.
 */
class VersionedEntityContractTest extends AbstractRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Each SQL check runs in its own fresh transaction to ensure a PostgreSQL
    // error (column not found) does not poison the outer test transaction.

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void immutable_entities_have_no_version_column() {
        for (String table : new String[]{"operations", "trx_state_historics", "ledgers"}) {
            assertThatThrownBy(() ->
                    jdbcTemplate.execute("SELECT version FROM " + table + " LIMIT 1"))
                    .isInstanceOf(BadSqlGrammarException.class)
                    .as("Table '%s' must NOT have a version column", table);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void mutable_entities_have_version_column() {
        for (String table : new String[]{"accounts", "transactions", "users", "customers"}) {
            assertThatCode(() ->
                    jdbcTemplate.execute("SELECT version FROM " + table + " LIMIT 1"))
                    .as("Table '%s' must have a version column", table)
                    .doesNotThrowAnyException();
        }
    }
}