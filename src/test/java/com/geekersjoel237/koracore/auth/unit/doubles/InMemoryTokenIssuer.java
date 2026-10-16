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
// a refresh token names its holder until its session is revoked; it can be exchanged once,
// and exchanging it again ends the session; revoking a token it never minted — an access
// token included — is refused, revoking one twice is not.
public class InMemoryTokenIssuer implements TokenIssuer {

    private record Issued(Id holder, String family) {
    }

    private final Map<String, Issued> refreshTokens = new HashMap<>();
    private final Set<String> used = new HashSet<>();
    private final Set<String> revokedFamilies = new HashSet<>();
    private final Clock clock;

    public InMemoryTokenIssuer(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Tokens issue(User user) {
        return mint(user, null);
    }

    @Override
    public Id holderOf(RefreshToken refreshToken) {
        return live(refreshToken).holder();
    }

    @Override
    public Tokens renew(RefreshToken refreshToken, User holder) {
        Issued issued = live(refreshToken);
        if (!used.add(refreshToken.value())) {
            revokedFamilies.add(issued.family());
            throw new InvalidTokenException();
        }
        return mint(holder, issued.family());
    }

    @Override
    public void revoke(RefreshToken refreshToken) {
        Issued issued = refreshTokens.get(refreshToken.value());
        if (issued == null)
            throw new InvalidTokenException();
        revokedFamilies.add(issued.family());
    }

    private Issued live(RefreshToken refreshToken) {
        Issued issued = refreshTokens.get(refreshToken.value());
        if (issued == null || revokedFamilies.contains(issued.family()))
            throw new InvalidTokenException();
        return issued;
    }

    private Tokens mint(User user, String family) {
        Instant now = Instant.now(clock);
        String refresh = "refresh-" + Id.generate().value();
        refreshTokens.put(refresh, new Issued(user.snapshot().id(), family == null ? refresh : family));
        return new Tokens(
                new Tokens.Issued<>(AccessToken.of("access-" + Id.generate().value()),
                        now.plus(Duration.ofMinutes(15))),
                new Tokens.Issued<>(RefreshToken.of(refresh), now.plus(Duration.ofDays(7))));
    }
}
