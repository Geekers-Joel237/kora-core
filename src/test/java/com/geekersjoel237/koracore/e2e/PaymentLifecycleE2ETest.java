package com.geekersjoel237.koracore.e2e;

import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.infrastructure.config.SecurityProperties;
import com.geekersjoel237.koracore.web.api.payment.cashIn.CashInRequest;
import com.geekersjoel237.koracore.web.api.payment.saga.PaymentRequest;
import com.geekersjoel237.koracore.web.api.payment.shared.TransactionResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentLifecycleE2ETest extends AbstractE2ETest {

    private static final String EMAIL     = "saga@example.com";
    private static final String FULL_NAME = "Saga User";
    private static final String PREFIX    = "+237";
    private static final String PHONE     = "690000099";
    private static final String PIN       = "4321";
    private static final BigDecimal AMOUNT   = new BigDecimal("10000.00");
    private static final String CURRENCY = "XOF";
    private static final String METHOD   = "ORANGE_MONEY";

    @Autowired
    private SecurityProperties securityProperties;

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void should_return_settlement_pending_on_successful_payment() {
        SetupData ctx = setupCustomerWithAccountAndBalance(EMAIL, FULL_NAME, PREFIX, PHONE, PIN, AMOUNT);

        ResponseEntity<TransactionResponse> resp = postWithToken(
                "/payments",
                new PaymentRequest(PIN, AMOUNT, CURRENCY, METHOD, "corr-saga-1"),
                ctx.tokens().accessToken(),
                TransactionResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        TransactionResponse tx = resp.getBody();
        assertThat(tx).isNotNull();
        assertThat(tx.transactionId()).isNotBlank();
        assertThat(tx.state()).isEqualTo("SETTLEMENT_PENDING");
        assertThat(tx.amount()).isEqualByComparingTo(AMOUNT);

        var account = accountRepository.findByCustomerId(ctx.customerId()).orElseThrow();
        assertThat(account.snapshot().balance().solde().value())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── error paths ───────────────────────────────────────────────────────────

    @Test
    void should_return_422_when_insufficient_funds() {
        // no cash-in → balance is 0
        SetupData ctx = setupCustomerWithAccount(EMAIL, FULL_NAME, PREFIX, PHONE, PIN);

        ResponseEntity<String> resp = postWithToken(
                "/payments",
                new PaymentRequest(PIN, AMOUNT, CURRENCY, METHOD, "corr-insuf"),
                ctx.tokens().accessToken(),
                String.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void should_return_401_when_wrong_pin() {
        SetupData ctx = setupCustomerWithAccountAndBalance(EMAIL, FULL_NAME, PREFIX, PHONE, PIN, AMOUNT);

        ResponseEntity<String> resp = postWithToken(
                "/payments",
                new PaymentRequest("0000", AMOUNT, CURRENCY, METHOD, "corr-wrongpin"),
                ctx.tokens().accessToken(),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_return_403_when_admin_token_used_on_payments_endpoint() {
        // Build an ADMIN JWT directly using the same key the JwtAuthenticationFilter
        // uses — no DB interaction needed.  The JWT carries role=ADMIN; Spring Security
        // blocks POST /payments (which requires ROLE_CUSTOMER) and returns 403.
        String adminToken = buildJwt(Id.generate().value(), "ADMIN");

        ResponseEntity<String> resp = postWithToken(
                "/payments",
                new PaymentRequest("irrelevant", AMOUNT, CURRENCY, METHOD, "corr-admin"),
                adminToken,
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String buildJwt(String subject, String role) {
        var key = Keys.hmacShaKeyFor(
                securityProperties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000L))
                .signWith(key)
                .compact();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private SetupData setupCustomerWithAccountAndBalance(
            String email, String fullName, String prefix, String phone, String pin,
            BigDecimal balance) {
        SetupData ctx = setupCustomerWithAccount(email, fullName, prefix, phone, pin);
        postWithToken(
                "/payments/cash-in",
                new CashInRequest(pin, balance, CURRENCY, METHOD),
                ctx.tokens().accessToken(),
                TransactionResponse.class);
        return ctx;
    }
}