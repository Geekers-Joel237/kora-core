package com.geekersjoel237.koracore.payment.unit.domain.transaction;

import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentMethodTest {

    @ParameterizedTest
    @CsvSource({
            "CARD, CARD",
            "ORANGE_MONEY, ORANGE_MONEY", "OM, ORANGE_MONEY",
            "MOBILE_MONEY, MOBILE_MONEY", "MOMO, MOBILE_MONEY",
            "WALLET, WALLET"})
    void a_payment_method_is_read_from_its_name_or_its_short_code(String value, PaymentMethod expected) {
        assertThat(PaymentMethod.fromValue(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"om", " Momo ", "orange_money"})
    void a_payment_method_is_read_whatever_its_case_and_surrounding_spaces(String value) {
        assertThat(PaymentMethod.fromValue(value)).isNotNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void a_blank_payment_method_is_refused(String value) {
        assertThatThrownBy(() -> PaymentMethod.fromValue(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Error, cannot accept empty payment method !");
    }

    @Test
    void an_unknown_payment_method_is_refused_and_named() {
        assertThatThrownBy(() -> PaymentMethod.fromValue("PAYPAL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown payment method: PAYPAL");
    }

    @Test
    void each_payment_method_answers_its_short_code() {
        assertThat(PaymentMethod.CARD.value()).isEqualTo("CARD");
        assertThat(PaymentMethod.ORANGE_MONEY.value()).isEqualTo("OM");
        assertThat(PaymentMethod.MOBILE_MONEY.value()).isEqualTo("MOMO");
        assertThat(PaymentMethod.WALLET.value()).isEqualTo("WALLET");
    }
}
