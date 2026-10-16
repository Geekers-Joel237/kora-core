package com.geekersjoel237.koracore.auth.unit.application;

import com.geekersjoel237.koracore.auth.application.command.LogoutCommand;
import com.geekersjoel237.koracore.auth.application.usecases.LogoutService;
import com.geekersjoel237.koracore.auth.domain.enums.Role;
import com.geekersjoel237.koracore.auth.domain.exception.InvalidTokenException;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.domain.vo.RefreshToken;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.auth.unit.doubles.InMemoryTokenIssuer;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.unit.doubles.DirectTransactionBoundary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogoutServiceTest {

    private InMemoryTokenIssuer tokenIssuer;
    private LogoutService service;
    private Tokens tokens;

    @BeforeEach
    void setUp() {
        tokenIssuer = new InMemoryTokenIssuer(Clock.systemUTC());
        service = new LogoutService(new DirectTransactionBoundary(), tokenIssuer);
        tokens = tokenIssuer.issue(
                User.create(new Id("user-001"), "Joel Geekers", "test@koracore.com", Role.CUSTOMER));
    }

    private LogoutCommand logoutWith(String value) {
        return new LogoutCommand(Id.generate(), RefreshToken.of(value));
    }

    @Test
    void should_end_the_session_of_the_refresh_token() {
        service.execute(logoutWith(tokens.refreshToken().token().value()));

        assertThatThrownBy(() -> tokenIssuer.holderOf(tokens.refreshToken().token()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void should_accept_logging_out_twice() {
        service.execute(logoutWith(tokens.refreshToken().token().value()));

        assertThatNoException().isThrownBy(
                () -> service.execute(logoutWith(tokens.refreshToken().token().value())));
    }

    @Test
    void should_refuse_an_access_token() {
        assertThatThrownBy(() -> service.execute(logoutWith(tokens.accessToken().token().value())))
                .isInstanceOf(InvalidTokenException.class);
    }
}
