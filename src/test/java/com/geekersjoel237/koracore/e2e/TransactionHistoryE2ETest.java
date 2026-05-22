package com.geekersjoel237.koracore.e2e;

import com.geekersjoel237.koracore.web.api.payment.cashIn.CashInRequest;
import com.geekersjoel237.koracore.web.api.payment.cashOut.CashOutRequest;
import com.geekersjoel237.koracore.web.api.payment.history.TransactionHistoryResponse;
import com.geekersjoel237.koracore.web.api.payment.transfer.TransferRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionHistoryE2ETest extends AbstractE2ETest {

    private static final String EMAIL_A  = "alice@history.test";
    private static final String EMAIL_B  = "bob@history.test";
    private static final String PHONE_A  = "700000001";
    private static final String PHONE_B  = "700000002";
    private static final String PREFIX   = "+237";
    private static final String PIN      = "123456";
    private static final String PM       = "ORANGE_MONEY";

    private SetupData alice;

    @BeforeEach
    void setupUsers() {
        alice = setupCustomerWithAccount(EMAIL_A, "Alice", PREFIX, PHONE_A, PIN);
        setupCustomerWithAccount(EMAIL_B, "Bob", PREFIX, PHONE_B, PIN);
    }

    private void cashIn(SetupData user, int amount) {
        postWithToken("/payments/cash-in",
                new CashInRequest(PIN, java.math.BigDecimal.valueOf(amount), "XOF", PM),
                user.tokens().accessToken(),
                Object.class);
    }

    private void cashOut(SetupData user, int amount) {
        postWithToken("/payments/cash-out",
                new CashOutRequest(PIN, java.math.BigDecimal.valueOf(amount), "XOF", PM),
                user.tokens().accessToken(),
                Object.class);
    }

    private void transfer(SetupData sender, String toPhone, int amount) {
        postWithToken("/payments/transfer",
                new TransferRequest(PIN, java.math.BigDecimal.valueOf(amount), "XOF", toPhone),
                sender.tokens().accessToken(),
                Object.class);
    }

    private ResponseEntity<TransactionHistoryResponse> getHistory(SetupData user, String query) {
        String path = "/payments/history" + (query != null ? "?" + query : "");
        return getWithToken(path, user.tokens().accessToken(), TransactionHistoryResponse.class);
    }

    // ── Test 1: empty history ─────────────────────────────────────────────────

    @Test
    void should_return_empty_history_for_new_account() {
        ResponseEntity<TransactionHistoryResponse> resp = getHistory(alice, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().transactions()).isEmpty();
        assertThat(resp.getBody().totalElements()).isZero();
    }

    // ── Test 2: 401 without token ─────────────────────────────────────────────

    @Test
    void should_return_401_without_token() {
        ResponseEntity<String> resp = http.getForEntity(url("/payments/history"), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Test 3: all transactions appear ──────────────────────────────────────

    @Test
    void should_return_all_transactions_for_customer() {
        cashIn(alice, 10_000);
        cashIn(alice, 5_000);
        cashOut(alice, 3_000);

        ResponseEntity<TransactionHistoryResponse> resp = getHistory(alice, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertNotNull(resp.getBody());
        assertThat(resp.getBody().transactions()).hasSize(3);
        assertThat(resp.getBody().totalElements()).isEqualTo(3);
    }

    // ── Test 4: filter by type ────────────────────────────────────────────────

    @Test
    void should_filter_by_type() {
        cashIn(alice, 10_000);
        cashOut(alice, 5_000);

        ResponseEntity<TransactionHistoryResponse> resp = getHistory(alice, "type=CASH_IN");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertNotNull(resp.getBody());
        assertThat(resp.getBody().transactions()).hasSize(1);
        assertThat(resp.getBody().transactions().getFirst().type()).isEqualTo("CASH_IN");
    }

    // ── Test 5: P2P direction and masked counterpart ──────────────────────────

    @Test
    void should_show_p2p_with_masked_counterpart_for_sender() {
        cashIn(alice, 10_000);
        transfer(alice, PREFIX + PHONE_B, 5_000);

        ResponseEntity<TransactionHistoryResponse> resp =
                getHistory(alice, "type=P2P_TRANSFER");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertNotNull(resp.getBody());
        assertThat(resp.getBody().transactions()).hasSize(1);
        TransactionHistoryResponse.TransactionItem tx = resp.getBody().transactions().getFirst();
        assertThat(tx.direction()).isEqualTo("OUTBOUND");
        // Bob's number: prefix=+237, local=700000002 → +237700***002
        assertThat(tx.counterpart()).isEqualTo("+237700***002");
    }

    // ── Test 6: pagination ────────────────────────────────────────────────────

    @Test
    void should_paginate_results() {
        cashIn(alice, 1_000);
        cashIn(alice, 2_000);
        cashIn(alice, 3_000);

        ResponseEntity<TransactionHistoryResponse> page0 =
                getHistory(alice, "page=0&size=2");
        ResponseEntity<TransactionHistoryResponse> page1 =
                getHistory(alice, "page=1&size=2");

        assertThat(page0.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertNotNull(page0.getBody());
        assertThat(page0.getBody().transactions()).hasSize(2);
        assertThat(page0.getBody().totalElements()).isEqualTo(3);
        assertThat(page0.getBody().hasNext()).isTrue();

        Assertions.assertNotNull(page1.getBody());
        assertThat(page1.getBody().transactions()).hasSize(1);
        assertThat(page1.getBody().hasNext()).isFalse();
    }
}