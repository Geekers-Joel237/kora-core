package com.geekersjoel237.koracore.auth.adapters.out.security;

import com.geekersjoel237.koracore.auth.config.SecurityProperties;
import com.geekersjoel237.koracore.auth.domain.enums.Role;
import com.geekersjoel237.koracore.auth.domain.exception.InvalidTokenException;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.domain.vo.AccessToken;
import com.geekersjoel237.koracore.auth.domain.vo.RefreshToken;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.auth.ports.out.security.TokenIssuer;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;


@Component
public class JwtTokenIssuer implements TokenIssuer {

    private static final String USE_CLAIM = "typ";

    private enum Use {
        ACCESS, REFRESH;

        String claim() {
            return name().toLowerCase();
        }
    }

    public record Bearer(Id userId, Role role) {
    }

    private final SecurityProperties securityProperties;
    private final RefreshTokenStore refreshTokens;
    private final Clock clock;

    public JwtTokenIssuer(SecurityProperties securityProperties, RefreshTokenStore refreshTokens,
                          Clock clock) {
        this.securityProperties = securityProperties;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Override
    public Tokens issue(User user) {
        return mint(user, null);
    }

    // Exchanges a refresh token once. The second exchange of the same token means two parties
    // hold it — the user and a thief, in some order — so the whole session is ended.
    @Override
    public Tokens renew(RefreshToken refreshToken, User holder) {
        Id spent = read(refreshToken.value(), Use.REFRESH)
                .filter(claims -> claims.getId() != null)
                .map(claims -> new Id(claims.getId()))
                .orElseThrow(InvalidTokenException::new);
        Instant now = Instant.now(clock);

        Optional<Id> family = refreshTokens.consume(spent, now);
        if (family.isEmpty()) {
            if (refreshTokens.wasUsed(spent))
                refreshTokens.revokeFamilyOfReplayed(spent, now);
            throw new InvalidTokenException();
        }
        return mint(holder, family.get());
    }

    // A null family opens a new one: the first token of a login names its own family.
    private Tokens mint(User user, Id family) {
        Instant now = Instant.now(clock);
        Instant accessExpiry = now.plus(
                Duration.ofMinutes(securityProperties.jwt().accessTokenExpirationMinutes()));
        Instant refreshExpiry = now.plus(
                Duration.ofDays(securityProperties.jwt().refreshTokenExpirationDays()));

        String access = Jwts.builder()
                .subject(user.snapshot().id().value())
                .id(Id.generate().value())
                .claim("email", user.snapshot().email())
                .claim("role", user.snapshot().role().name())
                .claim(USE_CLAIM, Use.ACCESS.claim())
                .issuedAt(Date.from(now))
                .expiration(Date.from(accessExpiry))
                .signWith(signingKey(Use.ACCESS))
                .compact();

        // Carries no email and no role: a refresh token proves who, nothing more.
        Id refreshId = Id.generate();
        refreshTokens.record(refreshId, user.snapshot().id(), family == null ? refreshId : family,
                refreshExpiry, now);
        String refresh = Jwts.builder()
                .subject(user.snapshot().id().value())
                .id(refreshId.value())
                .claim(USE_CLAIM, Use.REFRESH.claim())
                .issuedAt(Date.from(now))
                .expiration(Date.from(refreshExpiry))
                .signWith(signingKey(Use.REFRESH))
                .compact();

        return new Tokens(
                new Tokens.Issued<>(AccessToken.of(access), accessExpiry),
                new Tokens.Issued<>(RefreshToken.of(refresh), refreshExpiry));
    }

    @Override
    public Id holderOf(RefreshToken refreshToken) {
        return read(refreshToken.value(), Use.REFRESH)
                .filter(claims -> claims.getId() != null
                                  && refreshTokens.isLive(new Id(claims.getId())))
                .map(claims -> new Id(claims.getSubject()))
                .orElseThrow(InvalidTokenException::new);
    }

    @Override
    public void revoke(RefreshToken refreshToken) {
        Claims claims = read(refreshToken.value(), Use.REFRESH)
                .filter(c -> c.getId() != null)
                .orElseThrow(InvalidTokenException::new);
        refreshTokens.revokeFamilyOf(new Id(claims.getId()), Instant.now(clock));
    }

    public Optional<Bearer> authenticate(String accessToken) {
        return read(accessToken, Use.ACCESS).flatMap(JwtTokenIssuer::bearerOf);
    }

    private static Optional<Bearer> bearerOf(Claims claims) {
        String role = claims.get("role", String.class);
        try {
            return role == null
                    ? Optional.empty()
                    : Optional.of(new Bearer(new Id(claims.getSubject()), Role.valueOf(role)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // A token minted for the other use fails on its signature; one that carries no use
    // claim — minted before the claim existed — or the wrong one is refused, not guessed.
    private Optional<Claims> read(String compact, Use expected) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey(expected))
                    .build()
                    .parseSignedClaims(compact)
                    .getPayload();
            boolean usable = expected.claim().equals(claims.get(USE_CLAIM, String.class))
                             && claims.getSubject() != null && !claims.getSubject().isBlank();
            return usable ? Optional.of(claims) : Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private SecretKey signingKey(Use use) {
        String secret = switch (use) {
            case ACCESS -> securityProperties.jwt().accessSecret();
            case REFRESH -> securityProperties.jwt().refreshSecret();
        };
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
