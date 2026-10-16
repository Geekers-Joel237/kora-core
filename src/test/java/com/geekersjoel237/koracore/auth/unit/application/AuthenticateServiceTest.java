package com.geekersjoel237.koracore.auth.unit.application;

import com.geekersjoel237.koracore.auth.application.query.authenticate.AuthenticateQuery;
import com.geekersjoel237.koracore.auth.application.usecases.AuthenticateService;
import com.geekersjoel237.koracore.auth.domain.enums.Role;
import com.geekersjoel237.koracore.auth.domain.exception.InvalidTokenException;
import com.geekersjoel237.koracore.auth.domain.exception.TokenUseMismatchException;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.domain.vo.AccessToken;
import com.geekersjoel237.koracore.auth.domain.vo.AuthenticatedUser;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.auth.unit.doubles.InMemoryTokenIssuer;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticateServiceTest {

    private static final Id USER_ID = new Id("user-001");

    private InMemoryTokenIssuer tokenIssuer;
    private AuthenticateService service;

    @BeforeEach
    void setUp() {
        tokenIssuer = new InMemoryTokenIssuer(Clock.systemUTC());
        service = new AuthenticateService(tokenIssuer);
    }

    private Tokens issuedTo(Role role) {
        return tokenIssuer.issue(User.create(USER_ID, "Joel Geekers", "test@koracore.com", role));
    }

    private AuthenticateQuery bearer(String value) {
        return new AuthenticateQuery(AccessToken.of(value));
    }

    @Test
    void should_authenticate_the_holder_of_an_access_token() {
        Tokens tokens = issuedTo(Role.ADMIN);

        AuthenticatedUser user = service.execute(bearer(tokens.accessToken().token().value()));

        assertThat(user).isEqualTo(new AuthenticatedUser(USER_ID, Role.ADMIN));
    }

    @Test
    void should_refuse_a_refresh_token_presented_as_bearer() {
        Tokens tokens = issuedTo(Role.CUSTOMER);

        assertThatThrownBy(() -> service.execute(bearer(tokens.refreshToken().token().value())))
                .isInstanceOf(TokenUseMismatchException.class);
    }

    @Test
    void should_refuse_a_token_the_issuer_does_not_recognise() {
        assertThatThrownBy(() -> service.execute(bearer("forged")))
                .isInstanceOf(InvalidTokenException.class);
    }
}
