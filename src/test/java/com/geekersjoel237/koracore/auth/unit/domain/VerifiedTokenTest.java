package com.geekersjoel237.koracore.auth.unit.domain;

import com.geekersjoel237.koracore.auth.domain.enums.Role;
import com.geekersjoel237.koracore.auth.domain.enums.TokenUse;
import com.geekersjoel237.koracore.auth.domain.exception.TokenUseMismatchException;
import com.geekersjoel237.koracore.auth.domain.vo.AuthenticatedUser;
import com.geekersjoel237.koracore.auth.domain.vo.VerifiedToken;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerifiedTokenTest {

    private static final Id USER_ID = new Id("user-001");

    @Test
    void should_return_subject_when_use_matches() {
        VerifiedToken token = VerifiedToken.refresh(USER_ID);

        assertThat(token.subjectFor(TokenUse.REFRESH)).isEqualTo(USER_ID);
    }

    @Test
    void should_refuse_an_access_token_where_a_refresh_token_is_expected() {
        VerifiedToken token = VerifiedToken.access(USER_ID, Role.CUSTOMER);

        assertThatThrownBy(() -> token.subjectFor(TokenUse.REFRESH))
                .isInstanceOf(TokenUseMismatchException.class)
                .hasMessageContaining("refresh");
    }

    @Test
    void should_refuse_a_refresh_token_where_an_access_token_is_expected() {
        VerifiedToken token = VerifiedToken.refresh(USER_ID);

        assertThatThrownBy(() -> token.subjectFor(TokenUse.ACCESS))
                .isInstanceOf(TokenUseMismatchException.class)
                .hasMessageContaining("access");
    }

    @Test
    void should_authenticate_the_user_of_an_access_token() {
        VerifiedToken token = VerifiedToken.access(USER_ID, Role.ADMIN);

        assertThat(token.authenticatedUser()).isEqualTo(new AuthenticatedUser(USER_ID, Role.ADMIN));
    }

    @Test
    void should_not_authenticate_with_a_refresh_token() {
        VerifiedToken token = VerifiedToken.refresh(USER_ID);

        assertThatThrownBy(token::authenticatedUser)
                .isInstanceOf(TokenUseMismatchException.class);
    }

    @Test
    void should_require_a_role_on_an_access_token() {
        assertThatThrownBy(() -> new VerifiedToken(USER_ID, TokenUse.ACCESS, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_refuse_a_role_on_a_refresh_token() {
        assertThatThrownBy(() -> new VerifiedToken(USER_ID, TokenUse.REFRESH, Role.CUSTOMER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_missing_subject_or_use() {
        assertThatThrownBy(() -> new VerifiedToken(null, TokenUse.REFRESH, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VerifiedToken(USER_ID, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
