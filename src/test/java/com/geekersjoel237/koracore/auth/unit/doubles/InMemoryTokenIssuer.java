package com.geekersjoel237.koracore.auth.unit.doubles;

import com.geekersjoel237.koracore.auth.domain.exception.InvalidTokenException;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.domain.vo.AccessToken;
import com.geekersjoel237.koracore.auth.domain.vo.RefreshToken;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.auth.ports.out.security.TokenIssuer;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Opaque values instead of JWTs. Honours the port's contract the way the real issuer does:
// only a live refresh token it minted names a holder; revoking one it never minted — an
// access token included — is refused, revoking one twice is not.
public class InMemoryTokenIssuer implements TokenIssuer {

    private final Map<String, Id> refreshTokens = new HashMap<>();
    private final Set<String> revoked = new HashSet<>();
    private final Clock clock;

    public InMemoryTokenIssuer(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Tokens issue(User user) {
        Instant now = Instant.now(clock);
        String refresh = "refresh-" + Id.generate().value();
        refreshTokens.put(refresh, user.snapshot().id());
        return new Tokens(
                new Tokens.Issued<>(AccessToken.of("access-" + Id.generate().value()),
                        now.plus(Duration.ofMinutes(15))),
                new Tokens.Issued<>(RefreshToken.of(refresh), now.plus(Duration.ofDays(7))));
    }

    @Override
    public Id holderOf(RefreshToken refreshToken) {
        Id holder = refreshTokens.get(refreshToken.value());
        if (holder == null || revoked.contains(refreshToken.value()))
            throw new InvalidTokenException();
        return holder;
    }

    @Override
    public void revoke(RefreshToken refreshToken) {
        if (!refreshTokens.containsKey(refreshToken.value()))
            throw new InvalidTokenException();
        revoked.add(refreshToken.value());
    }
}
