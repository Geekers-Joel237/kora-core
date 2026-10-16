package com.geekersjoel237.koracore.auth.unit.domain;

import com.geekersjoel237.koracore.auth.domain.enums.TokenUse;
import com.geekersjoel237.koracore.auth.domain.exception.TokenUseMismatchException;
import com.geekersjoel237.koracore.auth.domain.vo.VerifiedToken;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerifiedTokenTest {

    private static final Id USER_ID = new Id("user-001");

    @Test
    void should_return_subject_when_use_matches() {
        VerifiedToken token = new VerifiedToken(USER_ID, TokenUse.REFRESH);

        assertThat(token.subjectFor(TokenUse.REFRESH)).isEqualTo(USER_ID);
    }

    @Test
    void should_refuse_an_access_token_where_a_refresh_token_is_expected() {
        VerifiedToken token = new VerifiedToken(USER_ID, TokenUse.ACCESS);

        assertThatThrownBy(() -> token.subjectFor(TokenUse.REFRESH))
                .isInstanceOf(TokenUseMismatchException.class)
                .hasMessageContaining("refresh");
    }

    @Test
    void should_refuse_a_refresh_token_where_an_access_token_is_expected() {
        VerifiedToken token = new VerifiedToken(USER_ID, TokenUse.REFRESH);

        assertThatThrownBy(() -> token.subjectFor(TokenUse.ACCESS))
                .isInstanceOf(TokenUseMismatchException.class)
                .hasMessageContaining("access");
    }

    @Test
    void should_reject_missing_subject_or_use() {
        assertThatThrownBy(() -> new VerifiedToken(null, TokenUse.ACCESS))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VerifiedToken(USER_ID, null))
                .isInstanceOf(NullPointerException.class);
    }
}
