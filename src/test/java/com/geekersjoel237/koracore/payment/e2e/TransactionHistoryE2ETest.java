package com.geekersjoel237.koracore.payment.e2e;

import com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashIn.CashInRequest;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashOut.CashOutRequest;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.history.TransactionHistoryResponse;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.history.TransactionHistoryResponse.Item;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.transfer.TransferRequest;
import com.geekersjoel237.koracore.shared.e2e.AbstractE2ETest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionHistoryE2ETest extends AbstractE2ETest {

    private static final String PREFIX = "+237";
    private static final String PHONE_A = "700000001";
    private static final String PHONE_B = "700000002";
    private static final String PIN = "123456";
    private static final String PM = "ORANGE_MONEY";

    private SetupData alice;
    private SetupData bob;

    @BeforeEach
    void setupUsers() {
        alice = setupCustomerWithAccount("alice@history.test", "Alice", PREFIX, PHONE_A, PIN);
        bob = setupCustomerWithAccount("bob@history.test", "Bob", PREFIX, PHONE_B, PIN);
    }

    private void cashIn(SetupData user, int amount) {
        postWithToken("/payments/cash-in", new CashInRequest(PIN, BigDecimal.valueOf(amount), "XAF", PM),
                user.tokens().accessToken(), Object.class);
    }

    private void cashOut(SetupData user, int amount) {
        postWithToken("/payments/cash-out", new CashOutRequest(PIN, BigDecimal.valueOf(amount), "XAF", PM),
                user.tokens().accessToken(), Object.class);
    }

    private void transferToBob(int amount) {
        postWithToken("/payments/transfer",
                new TransferRequest(PIN, BigDecimal.valueOf(amount), "XAF", PREFIX, PHONE_B),
                alice.tokens().accessToken(), Object.class);
    }

    private void aliceDepositsWithdrawsAndSends() {
        cashIn(alice, 10_000);
        cashOut(alice, 2_000);
        transferToBob(5_000);
    }

    private ResponseEntity<TransactionHistoryResponse> history(SetupData user, String query) {
        return getWithToken("/payments/history" + (query == null ? "" : "?" + query),
                user.tokens().accessToken(), TransactionHistoryResponse.class);
    }

    private ResponseEntity<String> rawHistory(String query) {
        return getWithToken("/payments/history?" + query, alice.tokens().accessToken(), String.class);
    }

    @Test
    void should_return_an_empty_history_for_a_new_account() {
        ResponseEntity<TransactionHistoryResponse> response = history(alice, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items()).isEmpty();
        assertThat(response.getBody().nextCursor()).isNull();
    }

    @Test
    void should_return_401_without_token() {
        assertThat(http.getForEntity(url("/payments/history"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_list_what_the_customer_did_most_recent_first() {
        aliceDepositsWithdrawsAndSends();

        TransactionHistoryResponse body = history(alice, null).getBody();

        assertThat(body.items()).extracting(Item::kind)
                .containsExactly("TRANSFER_SENT", "WITHDRAWAL", "DEPOSIT");
        assertThat(body.items()).extracting(Item::status).containsOnly("COMPLETED");
        assertThat(body.items()).extracting(Item::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("-5000"), new BigDecimal("-2000"), new BigDecimal("10000"));
        assertThat(body.items()).extracting(Item::currency).containsOnly("XAF");
        assertThat(body.items()).allSatisfy(item -> {
            assertThat(item.reference()).isNotBlank();
            assertThat(item.date()).isNotNull();
        });
    }

    @Test
    void should_name_the_channel_of_a_deposit_and_the_masked_counterpart_of_a_transfer() {
        aliceDepositsWithdrawsAndSends();

        TransactionHistoryResponse body = history(alice, null).getBody();
        Item sent = body.items().getFirst();
        Item deposit = body.items().getLast();

        assertThat(sent.counterpart()).isEqualTo("+237700***002");
        assertThat(sent.channel()).isNull();
        assertThat(deposit.channel()).isEqualTo("ORANGE_MONEY");
        assertThat(deposit.counterpart()).isNull();
    }

    @Test
    void should_show_the_receiver_a_received_transfer_from_the_masked_sender() {
        aliceDepositsWithdrawsAndSends();

        Item received = history(bob, null).getBody().items().getFirst();

        assertThat(received.kind()).isEqualTo("TRANSFER_RECEIVED");
        assertThat(received.amount()).isEqualByComparingTo("5000");
        assertThat(received.counterpart()).isEqualTo("+237700***001");
    }

    @Test
    void should_expose_no_lifecycle_detail() {
        aliceDepositsWithdrawsAndSends();

        String body = rawHistory("limit=20").getBody();

        assertThat(body).doesNotContain("stateHistory", "\"state\"", "transactionId", "SETTLED", "CAPTURED");
    }

    @Test
    void should_filter_by_kind() {
        aliceDepositsWithdrawsAndSends();

        assertThat(history(alice, "kind=DEPOSIT").getBody().items())
                .extracting(Item::kind).containsExactly("DEPOSIT");
    }

    @Test
    void should_filter_by_customer_status() {
        aliceDepositsWithdrawsAndSends();

        assertThat(history(alice, "status=COMPLETED").getBody().items()).hasSize(3);
        assertThat(history(alice, "status=FAILED").getBody().items()).isEmpty();
    }

    @Test
    void should_filter_by_calendar_days() {
        aliceDepositsWithdrawsAndSends();
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Douala"));

        assertThat(history(alice, "from=" + today + "&to=" + today).getBody().items()).hasSize(3);
        assertThat(history(alice, "from=2000-01-01&to=2000-01-31").getBody().items()).isEmpty();
    }

    @Test
    void should_page_through_the_history_with_the_cursor() {
        aliceDepositsWithdrawsAndSends();

        TransactionHistoryResponse first = history(alice, "limit=2").getBody();
        TransactionHistoryResponse second = history(alice, "limit=2&cursor=" + first.nextCursor()).getBody();

        assertThat(first.items()).extracting(Item::kind).containsExactly("TRANSFER_SENT", "WITHDRAWAL");
        assertThat(first.nextCursor()).isNotBlank();
        assertThat(second.items()).extracting(Item::kind).containsExactly("DEPOSIT");
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    void should_reject_an_unknown_status() {
        assertThat(rawHistory("status=SETTLED").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_reject_an_unknown_kind() {
        assertThat(rawHistory("kind=CASH_IN").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_reject_a_malformed_date() {
        assertThat(rawHistory("from=2026-13-01").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_reject_a_window_ending_before_it_starts() {
        assertThat(rawHistory("from=2026-10-02&to=2026-10-01").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_reject_a_forged_cursor() {
        assertThat(rawHistory("cursor=not-a-cursor").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_reject_a_limit_outside_its_bounds() {
        assertThat(rawHistory("limit=0").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rawHistory("limit=101").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
