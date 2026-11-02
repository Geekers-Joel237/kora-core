package com.geekersjoel237.koracore.auth.unit.config;

import com.geekersjoel237.koracore.auth.config.SecurityProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPropertiesTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static final String ACCESS = "test-access-key-must-be-at-least-32-chars!!";
    private static final String REFRESH = "test-refresh-key-must-be-at-least-32-chars!";

    private static Set<ConstraintViolation<SecurityProperties.Jwt>> violationsOf(String access,
                                                                                   String refresh) {
        return VALIDATOR.validate(new SecurityProperties.Jwt(access, refresh, 15, 7));
    }

    @Test
    void accepts_two_distinct_keys() {
        assertThat(violationsOf(ACCESS, REFRESH)).isEmpty();
    }

    @Test
    void refuses_the_same_key_for_both_uses() {
        assertThat(violationsOf(ACCESS, ACCESS))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("access-secret and refresh-secret must differ");
    }

    @Test
    void refuses_a_short_refresh_key() {
        assertThat(violationsOf(ACCESS, "too-short"))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("refreshSecret");
    }

    @Test
    void refuses_a_missing_access_key() {
        assertThat(violationsOf(null, REFRESH))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("accessSecret");
    }
}
