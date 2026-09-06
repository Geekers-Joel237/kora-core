package com.geekersjoel237.koracore.auth.adapters.out.security;

import com.geekersjoel237.koracore.auth.config.SecurityProperties;
import com.geekersjoel237.koracore.auth.ports.out.security.TokenIssuer;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.domain.vo.RefreshToken;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.auth.domain.exception.InvalidRefreshTokenException;
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


@Component
public class JwtTokenIssuer implements TokenIssuer {

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
                .issuedAt(Date.from(now))
                .expiration(Date.from(accessExpiry))
                .signWith(signingKey())
                .compact();

        // Carries no email and no role: a refresh token proves who, nothing more.
        String refresh = Jwts.builder()
                .subject(user.snapshot().id().value())
                .id(Id.generate().value())
                .issuedAt(Date.from(now))
                .expiration(Date.from(refreshExpiry))
                .signWith(signingKey())
                .compact();

        return new Tokens(
                new Tokens.TokenValue(access, accessExpiry),
                new Tokens.TokenValue(refresh, refreshExpiry));
    }

    @Override
    public Id subjectOf(RefreshToken refreshToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(refreshToken.value())
                    .getPayload();

            return new Id(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            // Translated here so no use case has to know what jjwt calls a bad token.
            throw new InvalidRefreshTokenException(e);
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(
                securityProperties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }
}
