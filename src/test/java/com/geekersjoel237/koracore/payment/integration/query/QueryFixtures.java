package com.geekersjoel237.koracore.payment.integration.query;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

/**
 * Rows written the short way, in SQL.
 *
 * <p>The read adapters answer questions about the schema, so the fixtures speak to the
 * schema directly. Building the same rows through four aggregates and their repositories
 * would make a read test depend on the write side's mapping, and a failure would no
 * longer say which of the two broke.
 */
final class QueryFixtures {

    private final NamedParameterJdbcTemplate jdbc;

    QueryFixtures(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A customer needs the user row its primary key points at. */
    void customer(String customerId, String prefix, String number) {
        jdbc.update("""
                INSERT INTO users (id, full_name, email, role, status, created_at, updated_at)
                VALUES (:id, 'Test User', :email, 'CUSTOMER', 'ACTIVE', now(), now())
                """, Map.of("id", customerId, "email", customerId + "@kora.test"));
        jdbc.update("""
                INSERT INTO customers (id, phone_prefix, phone_number, hashed_pin, created_at, updated_at)
                VALUES (:id, :prefix, :number, 'hash', now(), now())
                """, Map.of("id", customerId, "prefix", prefix, "number", number));
    }

    void wallet(String accountId, String customerId, String balance) {
        account(accountId, customerId, "CUSTOMER_ACCOUNT", balance);
    }

    void account(String accountId, String resourceId, String resourceType, String balance) {
        jdbc.update("""
                INSERT INTO accounts (id, account_number, resource_type, resource_id,
                                      balance_amount, balance_currency, created_at, updated_at)
                VALUES (:id, :number, :type, :resourceId, :balance, 'XAF', now(), now())
                """, new MapSqlParameterSource()
                .addValue("id", accountId)
                .addValue("number", "ACC-" + accountId)
                .addValue("type", resourceType)
                .addValue("resourceId", resourceId)
                .addValue("balance", new BigDecimal(balance)));
    }

    void transaction(String id, String fromAccountId, String toAccountId,
                     String type, String state, String amount, Instant occurredAt) {
        jdbc.update("""
                INSERT INTO transactions (id, transaction_number, from_account_id, to_account_id,
                                          state, type, payment_method, amount, currency,
                                          occurred_at, created_at, updated_at)
                VALUES (:id, :number, :from, :to, :state, :type, 'ORANGE_MONEY',
                        :amount, 'XAF', :occurredAt, now(), now())
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("number", "TRX-" + id)
                .addValue("from", fromAccountId)
                .addValue("to", toAccountId)
                .addValue("state", state)
                .addValue("type", type)
                .addValue("amount", new BigDecimal(amount))
                .addValue("occurredAt", Timestamp.from(occurredAt)));
    }

    void stateChange(String transactionId, String oldState, String newState, Instant occurredAt) {
        jdbc.update("""
                INSERT INTO trx_state_historics (id, transaction_id, old_state, new_state,
                                                 occurred_at, created_at, updated_at)
                VALUES (:id, :trx, :old, :new, :occurredAt, now(), now())
                """, new MapSqlParameterSource()
                .addValue("id", java.util.UUID.randomUUID().toString())
                .addValue("trx", transactionId)
                .addValue("old", oldState)
                .addValue("new", newState)
                .addValue("occurredAt", Timestamp.from(occurredAt)));
    }
}
