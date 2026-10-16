package com.geekersjoel237.koracore.auth.adapters.out.security;

import com.geekersjoel237.koracore.auth.config.SecurityProperties;
import com.geekersjoel237.koracore.auth.domain.enums.Role;
import com.geekersjoel237.koracore.auth.domain.vo.AccessToken;
import com.geekersjoel237.koracore.auth.ports.out.security.TokenIssuer;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.domain.vo.RefreshToken;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.auth.domain.exception.InvalidTokenException;
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
    private final Clock clock;

    public JwtTokenIssuer(SecurityProperties securityProperties, Clock clock) {
        this.securityProperties = securityProperties;
        this.clock = clock;
    }

    @Override
    public Tokens issue(User user) {
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
        String refresh = Jwts.builder()
                .subject(user.snapshot().id().value())
                .id(Id.generate().value())
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
                .map(claims -> new Id(claims.getSubject()))
                .orElseThrow(InvalidTokenException::new);
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
