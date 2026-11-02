package com.geekersjoel237.koracore.auth.unit.application;

import com.geekersjoel237.koracore.auth.application.command.RefreshTokensCommand;
import com.geekersjoel237.koracore.auth.application.usecases.RefreshTokensService;
import com.geekersjoel237.koracore.auth.domain.enums.Role;
import com.geekersjoel237.koracore.auth.domain.exception.CustomerNotFoundException;
import com.geekersjoel237.koracore.auth.domain.exception.InvalidTokenException;
import com.geekersjoel237.koracore.auth.domain.exception.UserNotActiveException;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.domain.vo.RefreshToken;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.auth.unit.doubles.InMemoryTokenIssuer;
import com.geekersjoel237.koracore.auth.unit.doubles.InMemoryUserRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.unit.doubles.DirectTransactionBoundary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokensServiceTest {

    private static final Id USER_ID = new Id("user-001");

    private InMemoryUserRepository users;
    private InMemoryTokenIssuer tokenIssuer;
    private RefreshTokensService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        tokenIssuer = new InMemoryTokenIssuer(Clock.systemUTC());
        service = new RefreshTokensService(new DirectTransactionBoundary(), users, tokenIssuer);
    }

    private User aUser() {
        return User.create(USER_ID, "Joel Geekers", "test@koracore.com", Role.CUSTOMER);
    }

    private Tokens loggedIn(User user) {
        users.save(user);
        return tokenIssuer.issue(user);
    }

    private RefreshTokensCommand refreshWith(String value) {
        return new RefreshTokensCommand(Id.generate(), RefreshToken.of(value));
    }

    @Test
    void should_issue_a_new_pair_for_a_refresh_token() {
        Tokens tokens = loggedIn(aUser());

        Tokens renewed = service.execute(refreshWith(tokens.refreshToken().token().value()));

        assertThat(tokenIssuer.holderOf(renewed.refreshToken().token())).isEqualTo(USER_ID);
        assertThat(renewed.refreshToken().token()).isNotEqualTo(tokens.refreshToken().token());
    }

    @Test
    void should_end_the_session_when_a_spent_refresh_token_is_replayed() {
        Tokens tokens = loggedIn(aUser());
        Tokens renewed = service.execute(refreshWith(tokens.refreshToken().token().value()));

        assertThatThrownBy(() -> service.execute(refreshWith(tokens.refreshToken().token().value())))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> service.execute(refreshWith(renewed.refreshToken().token().value())))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void should_refuse_an_access_token_presented_as_refresh_token() {
        Tokens tokens = loggedIn(aUser());

        assertThatThrownBy(() -> service.execute(refreshWith(tokens.accessToken().token().value())))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void should_refuse_a_token_the_issuer_does_not_recognise() {
        loggedIn(aUser());

        assertThatThrownBy(() -> service.execute(refreshWith("forged")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void should_refuse_a_refresh_token_whose_user_is_gone() {
        Tokens tokens = loggedIn(aUser());
        users.reset();

        assertThatThrownBy(() -> service.execute(refreshWith(tokens.refreshToken().token().value())))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void should_not_renew_access_for_a_suspended_user() {
        User user = aUser();
        Tokens tokens = loggedIn(user);
        user.suspend();
        users.save(user);

        assertThatThrownBy(() -> service.execute(refreshWith(tokens.refreshToken().token().value())))
                .isInstanceOf(UserNotActiveException.class);
    }
}
