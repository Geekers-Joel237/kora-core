package com.geekersjoel237.koracore.e2e;

import com.geekersjoel237.koracore.web.api.payment.cashIn.CashInRequest;
import com.geekersjoel237.koracore.web.api.payment.cashOut.CashOutRequest;
import com.geekersjoel237.koracore.web.api.payment.shared.TransactionResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E tests exercising provider failure paths through the real Spring context.
 * No mocking — behavior is controlled by {@code kora.provider.behavior} property.
 * Each nested class spins up its own application context with a different behavior.
 */
class ProviderFailureE2ETest {

    // ── When provider refuses authorization ───────────────────────────────────

    @Nested
    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {
                    "kora.provider.behavior=FAIL_ON_AUTHORIZE",
                    "kora.provider.simulate-latency=false"
            })
    @org.springframework.test.context.ActiveProfiles("test")
    @org.springframework.context.annotation.Import(TestMailConfig.class)
    class WhenProviderRefusesAuthorization extends AbstractE2ETest {

        @Test
        void cash_in_returns_authorization_failed_state() {
            SetupData ctx = setupCustomerWithAccount(
                    "fail-auth-ci@example.com", "FailAuthCI", "+225", "07000009001", "1234");

            ResponseEntity<TransactionResponse> response = postWithToken(
                    "/payments/cash-in",
                    new CashInRequest("1234", new BigDecimal("5000.00"), "XOF", "ORANGE_MONEY"),
                    ctx.tokens().accessToken(),
                    TransactionResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            TransactionResponse tx = response.getBody();
            assertThat(tx).isNotNull();
            assertThat(tx.state()).isEqualTo("AUTHORIZATION_FAILED");
        }

        @Test
        void cash_out_returns_authorization_failed_state() {
            SetupData ctx = setupCustomerWithAccount(
                    "fail-auth-co@example.com", "FailAuthCO", "+225", "07000009002", "1234");

            // Credit balance first via direct account manipulation (provider is broken, can't cash-in)
            var account = accountRepository.findByCustomerId(ctx.customerId()).orElseThrow();
            account.credit(com.geekersjoel237.koracore.domain.vo.Amount.of(
                    new BigDecimal("10000.00"), "XOF"));
            accountRepository.save(account);

            ResponseEntity<TransactionResponse> response = postWithToken(
                    "/payments/cash-out",
                    new CashOutRequest("1234", new BigDecimal("3000.00"), "XOF", "ORANGE_MONEY"),
                    ctx.tokens().accessToken(),
                    TransactionResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            TransactionResponse tx = response.getBody();
            assertThat(tx).isNotNull();
            assertThat(tx.state()).isEqualTo("AUTHORIZATION_FAILED");
        }
    }

    // ── When provider fails on capture ────────────────────────────────────────

    @Nested
    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {
                    "kora.provider.behavior=FAIL_ON_CAPTURE",
                    "kora.provider.simulate-latency=false"
            })
    @org.springframework.test.context.ActiveProfiles("test")
    @org.springframework.context.annotation.Import(TestMailConfig.class)
    class WhenProviderFailsOnCapture extends AbstractE2ETest {

        @Test
        void cash_in_returns_capture_failed_state() {
            SetupData ctx = setupCustomerWithAccount(
                    "fail-cap-ci@example.com", "FailCapCI", "+225", "07000009003", "1234");

            ResponseEntity<TransactionResponse> response = postWithToken(
                    "/payments/cash-in",
                    new CashInRequest("1234", new BigDecimal("5000.00"), "XOF", "ORANGE_MONEY"),
                    ctx.tokens().accessToken(),
                    TransactionResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            TransactionResponse tx = response.getBody();
            assertThat(tx).isNotNull();
            assertThat(tx.state()).isEqualTo("CAPTURE_FAILED");
        }

        @Test
        void cash_in_does_not_credit_balance_on_capture_failure() {
            SetupData ctx = setupCustomerWithAccount(
                    "fail-cap-bal@example.com", "FailCapBal", "+225", "07000009004", "1234");

            postWithToken("/payments/cash-in",
                    new CashInRequest("1234", new BigDecimal("5000.00"), "XOF", "ORANGE_MONEY"),
                    ctx.tokens().accessToken(), TransactionResponse.class);

            var account = accountRepository.findByCustomerId(ctx.customerId()).orElseThrow();
            assertThat(account.snapshot().balance().solde().value())
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}