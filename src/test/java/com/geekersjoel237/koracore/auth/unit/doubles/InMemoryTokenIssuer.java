package com.geekersjoel237.koracore.auth.unit.doubles;

import com.geekersjoel237.koracore.auth.domain.enums.TokenUse;
import com.geekersjoel237.koracore.auth.domain.exception.InvalidRefreshTokenException;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.domain.vo.AccessToken;
import com.geekersjoel237.koracore.auth.domain.vo.RefreshToken;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.auth.domain.vo.VerifiedToken;
import com.geekersjoel237.koracore.auth.ports.out.security.TokenIssuer;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

// Opaque values instead of JWTs. Like the real issuer, it reports the use a token was
// minted for and leaves the decision to the caller.
public class InMemoryTokenIssuer implements TokenIssuer {

    private final Map<String, VerifiedToken> issued = new HashMap<>();
    private final Clock clock;

    public InMemoryTokenIssuer(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Tokens issue(User user) {
        Id subject = user.snapshot().id();
        Instant now = Instant.now(clock);
        return new Tokens(
                new Tokens.Issued<>(AccessToken.of(mint(subject, TokenUse.ACCESS)),
                        now.plus(Duration.ofMinutes(15))),
                new Tokens.Issued<>(RefreshToken.of(mint(subject, TokenUse.REFRESH)),
                        now.plus(Duration.ofDays(7))));
    }

    @Override
    public VerifiedToken verify(RefreshToken refreshToken) {
        VerifiedToken token = issued.get(refreshToken.value());
        if (token == null)
            throw new InvalidRefreshTokenException();
        return token;
    }

    private String mint(Id subject, TokenUse use) {
        String value = use.name().toLowerCase() + "-" + Id.generate().value();
        issued.put(value, new VerifiedToken(subject, use));
        return value;
    }
}
