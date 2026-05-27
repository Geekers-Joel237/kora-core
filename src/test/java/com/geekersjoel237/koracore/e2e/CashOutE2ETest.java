package com.geekersjoel237.koracore.e2e;

import com.geekersjoel237.koracore.web.api.payment.cashIn.CashInRequest;
import com.geekersjoel237.koracore.web.api.payment.cashOut.CashOutRequest;
import com.geekersjoel237.koracore.web.api.payment.shared.TransactionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CashOutE2ETest extends AbstractE2ETest {

    private static final String EMAIL     = "cashout@example.com";
    private static final String FULL_NAME = "CashOut User";
    private static final String PREFIX    = "+225";
    private static final String PHONE     = "07000004001";
    private static final String PIN       = "9876";
    private static final BigDecimal CASH_IN_AMOUNT  = new BigDecimal("10000.00");
    private static final BigDecimal CASH_OUT_AMOUNT = new BigDecimal("4000.00");

    @Test
    void should_cash_out_and_return_completed_transaction() {
        SetupData ctx = setupCustomerWithAccount(EMAIL, FULL_NAME, PREFIX, PHONE, PIN);
        postWithToken("/payments/cash-in",
                new CashInRequest(PIN, CASH_IN_AMOUNT, "XOF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), TransactionResponse.class);

        ResponseEntity<TransactionResponse> response = postWithToken(
                "/payments/cash-out",
                new CashOutRequest(PIN, CASH_OUT_AMOUNT, "XOF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(),
                TransactionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TransactionResponse tx = response.getBody();
        assertThat(tx).isNotNull();
        assertThat(tx.transactionId()).isNotBlank();
        assertThat(tx.state()).isEqualTo("COMPLETED");
        assertThat(tx.amount()).isEqualByComparingTo(CASH_OUT_AMOUNT);
    }

    @Test
    void should_decrease_balance_after_cash_out() {
        SetupData ctx = setupCustomerWithAccount(EMAIL, FULL_NAME, PREFIX, PHONE, PIN);
        postWithToken("/payments/cash-in",
                new CashInRequest(PIN, CASH_IN_AMOUNT, "XOF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), TransactionResponse.class);

        postWithToken("/payments/cash-out",
                new CashOutRequest(PIN, CASH_OUT_AMOUNT, "XOF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), TransactionResponse.class);

        var account = accountRepository.findByCustomerId(ctx.customerId()).orElseThrow();
        assertThat(account.snapshot().balance().solde().value())
                .isEqualByComparingTo(CASH_IN_AMOUNT.subtract(CASH_OUT_AMOUNT));
    }

    @Test
    void should_return_balance_consistency_after_cash_in_and_cash_out() {
        SetupData ctx = setupCustomerWithAccount(EMAIL, FULL_NAME, PREFIX, PHONE, PIN);
        postWithToken("/payments/cash-in",
                new CashInRequest(PIN, CASH_IN_AMOUNT, "XOF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), TransactionResponse.class);
        postWithToken("/payments/cash-in",
                new CashInRequest(PIN, CASH_IN_AMOUNT, "XOF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), TransactionResponse.class);

        postWithToken("/payments/cash-out",
                new CashOutRequest(PIN, CASH_OUT_AMOUNT, "XOF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), TransactionResponse.class);

        var account = accountRepository.findByCustomerId(ctx.customerId()).orElseThrow();
        BigDecimal expected = CASH_IN_AMOUNT.multiply(BigDecimal.TWO).subtract(CASH_OUT_AMOUNT);
        assertThat(account.snapshot().balance().solde().value()).isEqualByComparingTo(expected);
    }

    @Test
    void should_return_422_when_insufficient_funds() {
        SetupData ctx = setupCustomerWithAccount(EMAIL, FULL_NAME, PREFIX, PHONE, PIN);
        // No cash-in → balance is 0

        ResponseEntity<String> response = postWithToken(
                "/payments/cash-out",
                new CashOutRequest(PIN, CASH_OUT_AMOUNT, "XOF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void should_return_401_when_wrong_pin_provided() {
        SetupData ctx = setupCustomerWithAccount(EMAIL, FULL_NAME, PREFIX, PHONE, PIN);
        postWithToken("/payments/cash-in",
                new CashInRequest(PIN, CASH_IN_AMOUNT, "XOF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), TransactionResponse.class);

        ResponseEntity<String> response = postWithToken(
                "/payments/cash-out",
                new CashOutRequest("0000", CASH_OUT_AMOUNT, "XOF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_return_401_when_no_bearer_token_provided() {
        ResponseEntity<String> response = http.postForEntity(
                url("/payments/cash-out"),
                new CashOutRequest(PIN, CASH_OUT_AMOUNT, "XOF", "ORANGE_MONEY"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}