package com.geekersjoel237.koracore.payment.e2e;

import com.geekersjoel237.koracore.shared.e2e.AbstractE2ETest;
import com.geekersjoel237.koracore.shared.e2e.TestMailConfig;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashIn.CashInRequest;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashOut.CashOutRequest;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.shared.TransactionResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderFailureE2ETest {

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
                    new CashInRequest("1234", new BigDecimal("5000"), "XAF", "ORANGE_MONEY"),
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
            account.credit(com.geekersjoel237.koracore.shared.domain.vo.Amount.of(
                    new BigDecimal("10000"), "XAF"));
            accountRepository.save(account);

            ResponseEntity<TransactionResponse> response = postWithToken(
                    "/payments/cash-out",
                    new CashOutRequest("1234", new BigDecimal("3000"), "XAF", "ORANGE_MONEY"),
                    ctx.tokens().accessToken(),
                    TransactionResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            TransactionResponse tx = response.getBody();
            assertThat(tx).isNotNull();
            assertThat(tx.state()).isEqualTo("AUTHORIZATION_FAILED");
            assertThat(accountRepository.findByCustomerId(ctx.customerId()).orElseThrow().available().value())
                    .isEqualByComparingTo("10000");
        }
    }

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
                    new CashInRequest("1234", new BigDecimal("5000"), "XAF", "ORANGE_MONEY"),
                    ctx.tokens().accessToken(),
                    TransactionResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            TransactionResponse tx = response.getBody();
            assertThat(tx).isNotNull();
            assertThat(tx.state()).isEqualTo("CAPTURE_FAILED");
        }

        @Test
        void cash_out_releases_its_funds_on_capture_failure() {
            SetupData ctx = setupCustomerWithAccount(
                    "fail-cap-co@example.com", "FailCapCO", "+225", "07000009005", "1234");
            var account = accountRepository.findByCustomerId(ctx.customerId()).orElseThrow();
            account.credit(com.geekersjoel237.koracore.shared.domain.vo.Amount.of(
                    new BigDecimal("10000"), "XAF"));
            accountRepository.save(account);

            ResponseEntity<TransactionResponse> response = postWithToken(
                    "/payments/cash-out",
                    new CashOutRequest("1234", new BigDecimal("3000"), "XAF", "ORANGE_MONEY"),
                    ctx.tokens().accessToken(),
                    TransactionResponse.class);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().state()).isEqualTo("CAPTURE_FAILED");
            var after = accountRepository.findByCustomerId(ctx.customerId()).orElseThrow();
            assertThat(after.available().value()).isEqualByComparingTo("10000");
            assertThat(after.snapshot().balance().solde().value()).isEqualByComparingTo("10000");
        }

        @Test
        void cash_in_does_not_credit_balance_on_capture_failure() {
            SetupData ctx = setupCustomerWithAccount(
                    "fail-cap-bal@example.com", "FailCapBal", "+225", "07000009004", "1234");

            postWithToken("/payments/cash-in",
                    new CashInRequest("1234", new BigDecimal("5000"), "XAF", "ORANGE_MONEY"),
                    ctx.tokens().accessToken(), TransactionResponse.class);

            var account = accountRepository.findByCustomerId(ctx.customerId()).orElseThrow();
            assertThat(account.snapshot().balance().solde().value())
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
    @Nested
    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {
                    "kora.provider.behavior=TIMEOUT",
                    "kora.provider.simulate-latency=false"
            })
    @org.springframework.test.context.ActiveProfiles("test")
    @org.springframework.context.annotation.Import(TestMailConfig.class)
    class WhenProviderTimesOut extends AbstractE2ETest {

        @Test
        void cash_in_returns_unknown_state() {
            SetupData ctx = setupCustomerWithAccount(
                    "timeout-ci@example.com", "TimeoutCI", "+225", "07000009006", "1234");

            ResponseEntity<TransactionResponse> response = postWithToken(
                    "/payments/cash-in",
                    new CashInRequest("1234", new BigDecimal("5000"), "XAF", "ORANGE_MONEY"),
                    ctx.tokens().accessToken(),
                    TransactionResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().state()).isEqualTo("UNKNOWN");
        }

        @Test
        void cash_out_returns_unknown_state_and_keeps_its_funds_held() {
            SetupData ctx = setupCustomerWithAccount(
                    "timeout-co@example.com", "TimeoutCO", "+225", "07000009007", "1234");
            var account = accountRepository.findByCustomerId(ctx.customerId()).orElseThrow();
            account.credit(com.geekersjoel237.koracore.shared.domain.vo.Amount.of(
                    new BigDecimal("10000"), "XAF"));
            accountRepository.save(account);

            ResponseEntity<TransactionResponse> response = postWithToken(
                    "/payments/cash-out",
                    new CashOutRequest("1234", new BigDecimal("3000"), "XAF", "ORANGE_MONEY"),
                    ctx.tokens().accessToken(),
                    TransactionResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().state()).isEqualTo("UNKNOWN");
            var after = accountRepository.findByCustomerId(ctx.customerId()).orElseThrow();
            assertThat(after.snapshot().balance().solde().value()).isEqualByComparingTo("10000");
            assertThat(after.available().value()).isEqualByComparingTo("7000");
        }
    }
}
