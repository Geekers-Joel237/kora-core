package com.geekersjoel237.koracore.auth.integration.security;

import com.geekersjoel237.koracore.auth.adapters.out.persistence.JpaUserRepository;
import com.geekersjoel237.koracore.auth.adapters.out.security.JwtTokenIssuer;
import com.geekersjoel237.koracore.auth.config.SecurityProperties;
import com.geekersjoel237.koracore.auth.domain.enums.Role;
import com.geekersjoel237.koracore.auth.domain.exception.InvalidTokenException;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.domain.vo.RefreshToken;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.integration.persistence.AbstractRepositoryTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenIssuerTest extends AbstractRepositoryTest {

    @Autowired
    private JwtTokenIssuer issuer;

    @Autowired
    private SecurityProperties security;

    @Autowired
    private JpaUserRepository users;

    @PersistenceContext
    private EntityManager em;

    private Id userId;
    private User user;

    @BeforeEach
    void aUserExists() {
        userId = Id.generate();
        user = User.create(userId, "Grace Hopper", userId.value() + "@example.com", Role.CUSTOMER);
        users.save(user);
        em.flush();
    }

    private String signed(String secret, String use, String role, String jti) {
        var builder = Jwts.builder()
                .subject(userId.value())
                .expiration(Date.from(Instant.now().plus(1, DAYS)));
        if (use != null)
            builder.claim("typ", use);
        if (role != null)
            builder.claim("role", role);
        if (jti != null)
            builder.id(jti);
        return builder.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }


    // ── issuing ──────────────────────────────────────────────────────────────

    @Test
    void should_issue_an_access_token_living_fifteen_minutes() {
        Instant before = Instant.now();
        Tokens tokens = issuer.issue(user);

        assertThat(tokens.accessToken().expiresAt())
                .isAfter(before.plus(14, MINUTES))
                .isBefore(Instant.now().plus(16, MINUTES));
    }

    @Test
    void should_issue_a_refresh_token_living_seven_days() {
        Instant before = Instant.now();
        Tokens tokens = issuer.issue(user);

        assertThat(tokens.refreshToken().expiresAt())
                .isAfter(before.plus(6, DAYS).plus(23, HOURS))
                .isBefore(Instant.now().plus(7, DAYS).plus(1, HOURS));
    }

    @Test
    void should_issue_distinct_tokens_on_successive_calls() {
        Tokens t1 = issuer.issue(user);
        Tokens t2 = issuer.issue(user);

        assertThat(t1.accessToken().token()).isNotEqualTo(t2.accessToken().token());
        assertThat(t1.refreshToken().token()).isNotEqualTo(t2.refreshToken().token());
    }

    // ── refresh tokens ───────────────────────────────────────────────────────

    @Test
    void should_name_the_holder_of_a_refresh_token() {
        Tokens tokens = issuer.issue(user);

        assertThat(issuer.holderOf(tokens.refreshToken().token())).isEqualTo(userId);
    }

    @Test
    void should_refuse_an_access_token_presented_for_refresh() {
        Tokens tokens = issuer.issue(user);
        RefreshToken misused = RefreshToken.of(tokens.accessToken().token().value());

        assertThatThrownBy(() -> issuer.holderOf(misused)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void should_refuse_a_refresh_claim_signed_with_the_access_key() {
        String forged = signed(security.jwt().accessSecret(), "refresh", null, Id.generate().value());

        assertThatThrownBy(() -> issuer.holderOf(RefreshToken.of(forged)))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void should_refuse_an_access_claim_signed_with_the_refresh_key() {
        String confused = signed(security.jwt().refreshSecret(), "access", null, Id.generate().value());

        assertThatThrownBy(() -> issuer.holderOf(RefreshToken.of(confused)))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void should_refuse_a_signed_token_without_use_claim() {
        String legacy = signed(security.jwt().refreshSecret(), null, null, Id.generate().value());

        assertThatThrownBy(() -> issuer.holderOf(RefreshToken.of(legacy)))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void should_refuse_a_token_signed_with_another_key() {
        String forged = signed("another-secret-key-also-at-least-32-chars", "refresh", null,
                Id.generate().value());

        assertThatThrownBy(() -> issuer.holderOf(RefreshToken.of(forged)))
                .isInstanceOf(InvalidTokenException.class);
    }

    // A genuine signature is not enough any more: the issuer must remember the token.
    @Test
    void should_refuse_a_well_signed_refresh_token_it_never_issued() {
        String unknown = signed(security.jwt().refreshSecret(), "refresh", null, Id.generate().value());

        assertThatThrownBy(() -> issuer.holderOf(RefreshToken.of(unknown)))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void should_refuse_a_refresh_token_without_id() {
        String anonymous = signed(security.jwt().refreshSecret(), "refresh", null, null);

        assertThatThrownBy(() -> issuer.holderOf(RefreshToken.of(anonymous)))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void should_refuse_a_refresh_token_once_revoked() {
        Tokens tokens = issuer.issue(user);
        issuer.revoke(tokens.refreshToken().token());

        assertThatThrownBy(() -> issuer.holderOf(tokens.refreshToken().token()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void should_accept_revoking_the_same_refresh_token_twice() {
        Tokens tokens = issuer.issue(user);
        issuer.revoke(tokens.refreshToken().token());

        assertThatNoException().isThrownBy(() -> issuer.revoke(tokens.refreshToken().token()));
    }

    @Test
    void should_end_only_the_session_of_the_revoked_token() {
        Tokens phone = issuer.issue(user);
        Tokens tablet = issuer.issue(user);

        issuer.revoke(phone.refreshToken().token());

        assertThat(issuer.holderOf(tablet.refreshToken().token())).isEqualTo(userId);
    }

    @Test
    void should_refuse_to_revoke_an_access_token() {
        Tokens tokens = issuer.issue(user);
        RefreshToken misused = RefreshToken.of(tokens.accessToken().token().value());

        assertThatThrownBy(() -> issuer.revoke(misused)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void should_refuse_to_revoke_a_token_signed_with_another_key() {
        String forged = signed("another-secret-key-also-at-least-32-chars", "refresh", null,
                Id.generate().value());

        assertThatThrownBy(() -> issuer.revoke(RefreshToken.of(forged)))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ── access tokens ────────────────────────────────────────────────────────

    @Test
    void should_authenticate_the_bearer_of_an_access_token() {
        Tokens tokens = issuer.issue(user);

        assertThat(issuer.authenticate(tokens.accessToken().token().value()))
                .contains(new JwtTokenIssuer.Bearer(userId, Role.CUSTOMER));
    }

    @Test
    void should_not_authenticate_with_a_refresh_token() {
        Tokens tokens = issuer.issue(user);

        assertThat(issuer.authenticate(tokens.refreshToken().token().value())).isEmpty();
    }

    @Test
    void should_not_authenticate_a_refresh_claim_even_under_the_access_key() {
        String confused = signed(security.jwt().accessSecret(), "refresh", "CUSTOMER", null);

        assertThat(issuer.authenticate(confused)).isEmpty();
    }

    @Test
    void should_not_authenticate_an_unknown_or_missing_role() {
        assertThat(issuer.authenticate(signed(security.jwt().accessSecret(), "access", "ROOT", null)))
                .isEmpty();
        assertThat(issuer.authenticate(signed(security.jwt().accessSecret(), "access", null, null)))
                .isEmpty();
    }

    @Test
    void should_not_authenticate_garbage() {
        assertThat(issuer.authenticate("not-a-jwt")).isEmpty();
    }
}
