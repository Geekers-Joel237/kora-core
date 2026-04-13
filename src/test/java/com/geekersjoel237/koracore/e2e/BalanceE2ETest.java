package com.geekersjoel237.koracore.e2e;

import com.geekersjoel237.koracore.web.api.payment.balance.BalanceResponse;
import com.geekersjoel237.koracore.web.api.payment.cashIn.CashInRequest;
import com.geekersjoel237.koracore.web.api.payment.shared.TransactionResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BalanceE2ETest extends AbstractE2ETest {

    private static final String EMAIL     = "wallet@example.com";
    private static final String FULL_NAME = "Wallet User";
    private static final String PREFIX    = "+225";
    private static final String PHONE     = "07000005001";
    private static final String PIN       = "4321";
    private static final BigDecimal CASH_IN_AMOUNT = new BigDecimal("7500.00");

    @Test
    void should_return_zero_balance_for_newly_registered_account() {
        SetupData ctx = setupCustomerWithAccount(EMAIL, FULL_NAME, PREFIX, PHONE, PIN);

        ResponseEntity<BalanceResponse> response = getWithToken(
                "/payments/balance", ctx.tokens().accessToken(), BalanceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        BalanceResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.accountId()).isNotBlank();
        assertThat(body.accountNumber()).matches("ACC-\\d{8}-[A-Z0-9]{4}");
        assertThat(body.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(body.currency()).isEqualTo("XOF");
    }

    @Test
    void should_return_correct_balance_after_cash_in() {
        SetupData ctx = setupCustomerWithAccount(EMAIL, FULL_NAME, PREFIX, PHONE, PIN);

        postWithToken("/payments/cash-in",
                new CashInRequest(PIN, CASH_IN_AMOUNT, "XOF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), TransactionResponse.class);

        ResponseEntity<BalanceResponse> response = getWithToken(
                "/payments/balance", ctx.tokens().accessToken(), BalanceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().amount()).isEqualByComparingTo(CASH_IN_AMOUNT);
    }

    @Test
    void should_return_correct_balance_after_two_cash_ins() {
        SetupData ctx = setupCustomerWithAccount(EMAIL, FULL_NAME, PREFIX, PHONE, PIN);

        postWithToken("/payments/cash-in",
                new CashInRequest(PIN, CASH_IN_AMOUNT, "XOF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), TransactionResponse.class);
        postWithToken("/payments/cash-in",
                new CashInRequest(PIN, CASH_IN_AMOUNT, "XOF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), TransactionResponse.class);

        ResponseEntity<BalanceResponse> response = getWithToken(
                "/payments/balance", ctx.tokens().accessToken(), BalanceResponse.class);

        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().amount())
                .isEqualByComparingTo(CASH_IN_AMOUNT.multiply(BigDecimal.TWO));
    }

    @Test
    void should_return_401_when_no_bearer_token_provided() {
        ResponseEntity<String> response = http.getForEntity(
                url("/payments/balance"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}