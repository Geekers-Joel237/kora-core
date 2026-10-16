package com.geekersjoel237.koracore.auth.unit.application;

import com.geekersjoel237.koracore.auth.application.command.RefreshTokensCommand;
import com.geekersjoel237.koracore.auth.application.usecases.RefreshTokensService;
import com.geekersjoel237.koracore.auth.domain.enums.Role;
import com.geekersjoel237.koracore.auth.domain.exception.CustomerNotFoundException;
import com.geekersjoel237.koracore.auth.domain.exception.InvalidTokenException;
import com.geekersjoel237.koracore.auth.domain.exception.TokenUseMismatchException;
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

    private Tokens loggedIn() {
        User user = User.create(USER_ID, "Joel Geekers", "test@koracore.com", Role.CUSTOMER);
        users.save(user);
        return tokenIssuer.issue(user);
    }

    private RefreshTokensCommand refreshWith(String value) {
        return new RefreshTokensCommand(Id.generate(), RefreshToken.of(value));
    }

    @Test
    void should_issue_a_new_pair_for_a_refresh_token() {
        Tokens tokens = loggedIn();

        Tokens renewed = service.execute(refreshWith(tokens.refreshToken().token().value()));

        assertThat(tokenIssuer.verify(renewed.refreshToken().token()).subject()).isEqualTo(USER_ID);
        assertThat(renewed.refreshToken().token()).isNotEqualTo(tokens.refreshToken().token());
    }

    @Test
    void should_refuse_an_access_token_presented_as_refresh_token() {
        Tokens tokens = loggedIn();

        assertThatThrownBy(() -> service.execute(refreshWith(tokens.accessToken().token().value())))
                .isInstanceOf(TokenUseMismatchException.class);
    }

    @Test
    void should_refuse_a_token_the_issuer_does_not_recognise() {
        loggedIn();

        assertThatThrownBy(() -> service.execute(refreshWith("forged")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void should_refuse_a_refresh_token_whose_user_is_gone() {
        Tokens tokens = loggedIn();
        users.reset();

        assertThatThrownBy(() -> service.execute(refreshWith(tokens.refreshToken().token().value())))
                .isInstanceOf(CustomerNotFoundException.class);
    }
}
