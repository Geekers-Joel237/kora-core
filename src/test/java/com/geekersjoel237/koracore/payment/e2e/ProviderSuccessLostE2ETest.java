package com.geekersjoel237.koracore.payment.e2e;

import com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashIn.CashInRequest;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashOut.CashOutRequest;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.shared.TransactionResponse;
import com.geekersjoel237.koracore.shared.e2e.AbstractE2ETest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("L-2 — closed by recording the provider's answer before TX-2")
class ProviderSuccessLostE2ETest extends AbstractE2ETest {

    private static final String PIN = "1234";
    private static final BigDecimal AMOUNT = new BigDecimal("10000");

    @Autowired
    private JdbcTemplate jdbc;

    private void failTx2WithSqlState(String sqlState) {
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION l2_fail_tx2() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'TX-2 failure injected by ProviderSuccessLostE2ETest'
                        USING ERRCODE = '%s';
                END;
                $$ LANGUAGE plpgsql
                """.formatted(sqlState));
        jdbc.execute("""
                CREATE TRIGGER l2_fail_tx2 BEFORE INSERT ON ledger_entries
                FOR EACH ROW EXECUTE FUNCTION l2_fail_tx2()
                """);
    }

    @AfterEach
    void removeFailure() {
        jdbc.execute("DROP TRIGGER IF EXISTS l2_fail_tx2 ON ledger_entries");
        jdbc.execute("DROP FUNCTION IF EXISTS l2_fail_tx2()");
    }

    private String transactionOfType(String type) {
        List<String> ids = jdbc.queryForList(
                "SELECT id FROM transactions WHERE type = ?", String.class, type);
        assertThat(ids).describedAs("the transaction TX-1 created").hasSize(1);
        return ids.getFirst();
    }

    private String stateOf(String transactionId) {
        return jdbc.queryForObject("SELECT state FROM transactions WHERE id = ?",
                String.class, transactionId);
    }

    private List<String> providerReferencesOf(String transactionId) {
        return jdbc.queryForList("""
                SELECT provider_reference FROM authorization_records WHERE transaction_id = ?
                UNION ALL
                SELECT provider_ref FROM trx_state_historics
                WHERE transaction_id = ? AND provider_ref IS NOT NULL
                """, String.class, transactionId, transactionId);
    }

    @Test
    void a_cash_in_the_provider_collected_keeps_a_trace_when_tx2_keeps_failing() {
        SetupData ctx = setupCustomerWithAccount("l2-ci@example.com", "L2CashIn", "+225", "07000007001", PIN);
        failTx2WithSqlState("40P01");

        int status = postWithToken("/payments/cash-in", new CashInRequest(PIN, AMOUNT, "XAF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), String.class).getStatusCode().value();
        String tx = transactionOfType("CASH_IN");

        assertThat(providerReferencesOf(tx))
                .describedAs("the provider collected the money (HTTP %d): its reference must be on record", status)
                .isNotEmpty();
        assertThat(stateOf(tx))
                .describedAs("a cash-in the provider confirmed must not look like one it never saw")
                .isNotEqualTo("INITIALIZED");
    }

    @Test
    void a_cash_out_the_provider_paid_keeps_a_trace_when_tx2_fails() {
        SetupData ctx = setupCustomerWithAccount("l2-co@example.com", "L2CashOut", "+225", "07000007002", PIN);
        var deposit = postWithToken("/payments/cash-in", new CashInRequest(PIN, AMOUNT, "XAF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), TransactionResponse.class);
        assertThat(deposit.getStatusCode()).isEqualTo(HttpStatus.OK);
        failTx2WithSqlState("P0001");

        int status = postWithToken("/payments/cash-out", new CashOutRequest(PIN, AMOUNT, "XAF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), String.class).getStatusCode().value();
        String tx = transactionOfType("CASH_OUT");

        assertThat(providerReferencesOf(tx))
                .describedAs("the provider paid the money out (HTTP %d): its reference must be on record", status)
                .isNotEmpty();
        assertThat(stateOf(tx))
                .describedAs("a cash-out the provider confirmed must not look like one it never saw")
                .isNotEqualTo("INITIALIZED");
    }
}
