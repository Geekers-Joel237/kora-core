package com.geekersjoel237.koracore.e2e;

import com.geekersjoel237.koracore.web.api.payment.CashInRequest;
import com.geekersjoel237.koracore.web.api.payment.TransactionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CashInE2ETest extends AbstractE2ETest {

    private static final String EMAIL    = "bob@example.com";
    private static final String FULL_NAME = "Bob";
    private static final String PREFIX   = "+225";
    private static final String PHONE    = "07000002001";
    private static final String PIN      = "5678";
    private static final BigDecimal AMOUNT = new BigDecimal("5000.00");

    @Test
    void should_cash_in_and_return_completed_transaction() {
        SetupData ctx = setupCustomerWithAccount(EMAIL, FULL_NAME, PREFIX, PHONE, PIN);

        ResponseEntity<TransactionResponse> response = postWithToken(
                "/payments/cash-in",
                new CashInRequest(PIN, AMOUNT, "XOF", "ORANGE_MONEY"),
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
                new CashInRequest(PIN, AMOUNT, "XOF", "ORANGE_MONEY"),
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
                new CashInRequest(PIN, AMOUNT, "XOF", "ORANGE_MONEY"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_return_401_when_wrong_pin_provided() {
        SetupData ctx = setupCustomerWithAccount(EMAIL, FULL_NAME, PREFIX, PHONE, PIN);

        ResponseEntity<String> response = postWithToken(
                "/payments/cash-in",
                new CashInRequest("0000", AMOUNT, "XOF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

//    @Test
//    void should_return_404_when_customer_has_no_account() {
//        // Register customer but do NOT create an account
//        register(FULL_NAME, EMAIL, PREFIX, PHONE, PIN);
//        String otp = waitAndGetOtpCode(EMAIL);
//        var tokens = verifyOtp(EMAIL, otp).getBody();
//
//        ResponseEntity<String> response = postWithToken(
//                "/payments/cash-in",
//                new CashInRequest(PIN, AMOUNT, "XOF", "ORANGE_MONEY"),
//                tokens.accessToken(),
//                String.class);
//
//        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
//    }
}