package com.geekersjoel237.koracore.e2e;

import com.geekersjoel237.koracore.web.api.payment.cashIn.CashInRequest;
import com.geekersjoel237.koracore.web.api.payment.shared.TransactionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CashInE2ETest extends AbstractE2ETest {

    private static final String EMAIL    = "bob@example.com";
    private static final String FULL_NAME = "Bob";
    private static final String PREFIX   = "+225";
    private static final String PHONE    = "07000002001";
    private static final String PIN      = "5678";
    private static final BigDecimal AMOUNT = new BigDecimal("5000");

    @Test
    void should_cash_in_and_return_completed_transaction() {
        SetupData ctx = setupCustomerWithAccount(EMAIL, FULL_NAME, PREFIX, PHONE, PIN);

        ResponseEntity<TransactionResponse> response = postWithToken(
                "/payments/cash-in",
                new CashInRequest(PIN, AMOUNT, "XAF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(),
                TransactionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TransactionResponse tx = response.getBody();
        assertThat(tx).isNotNull();
        assertThat(tx.transactionId()).isNotBlank();
        assertThat(tx.state()).isEqualTo("COMPLETED");
        assertThat(tx.amount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void should_increase_balance_after_cash_in() {
        SetupData ctx = setupCustomerWithAccount(EMAIL, FULL_NAME, PREFIX, PHONE, PIN);

        postWithToken("/payments/cash-in",
                new CashInRequest(PIN, AMOUNT, "XAF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), TransactionResponse.class);

        var account = accountRepository.findByCustomerId(ctx.customerId()).orElseThrow();
        assertThat(account.snapshot().balance().solde().value())
                .isEqualByComparingTo(AMOUNT);
    }

    @Test
    void should_return_401_when_no_bearer_token_provided() {
        setupCustomerWithAccount(EMAIL, FULL_NAME, PREFIX, PHONE, PIN);

        ResponseEntity<String> response = http.postForEntity(
                url("/payments/cash-in"),
                new CashInRequest(PIN, AMOUNT, "XAF", "ORANGE_MONEY"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_return_401_when_wrong_pin_provided() {
        SetupData ctx = setupCustomerWithAccount(EMAIL, FULL_NAME, PREFIX, PHONE, PIN);

        ResponseEntity<String> response = postWithToken(
                "/payments/cash-in",
                new CashInRequest("0000", AMOUNT, "XAF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Validation tests ──────────────────────────────────────────────────────

    @Test
    void should_return_400_when_amount_is_null() {
        SetupData ctx = setupCustomerWithAccount(EMAIL, FULL_NAME, PREFIX, PHONE, PIN);
        ResponseEntity<String> response = postWithToken(
                "/payments/cash-in",
                Map.of("rawPin", PIN, "currency", "XAF", "paymentMethod", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("amount");
    }

    @Test
    void should_return_400_when_amount_is_zero() {
        SetupData ctx = setupCustomerWithAccount(EMAIL, FULL_NAME, PREFIX, PHONE, PIN);
        ResponseEntity<String> response = postWithToken(
                "/payments/cash-in",
                new CashInRequest(PIN, BigDecimal.ZERO, "XAF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("amount");
    }

    @Test
    void should_return_400_when_currency_is_blank() {
        SetupData ctx = setupCustomerWithAccount(EMAIL, FULL_NAME, PREFIX, PHONE, PIN);
        ResponseEntity<String> response = postWithToken(
                "/payments/cash-in",
                new CashInRequest(PIN, BigDecimal.valueOf(5000), "", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("currency");
    }
}