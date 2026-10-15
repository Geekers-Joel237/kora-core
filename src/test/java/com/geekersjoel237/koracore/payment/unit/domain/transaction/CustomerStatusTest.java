package com.geekersjoel237.koracore.payment.unit.domain.transaction;

import com.geekersjoel237.koracore.payment.domain.enums.CustomerStatus;
import com.geekersjoel237.koracore.payment.domain.model.state.TransactionState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static com.geekersjoel237.koracore.payment.domain.model.state.TransactionState.*;
import static org.assertj.core.api.Assertions.assertThat;

class CustomerStatusTest {

    @Test
    void should_show_pending_until_the_provider_captured() {
        assertThat(CustomerStatus.of(INITIALIZED)).isEqualTo(CustomerStatus.PENDING);
        assertThat(CustomerStatus.of(AUTHORIZED)).isEqualTo(CustomerStatus.PENDING);
        assertThat(CustomerStatus.of(UNKNOWN)).isEqualTo(CustomerStatus.PENDING);
    }

    @Test
    void should_show_completed_from_capture_onwards() {
        assertThat(CustomerStatus.of(CAPTURED)).isEqualTo(CustomerStatus.COMPLETED);
        assertThat(CustomerStatus.of(SETTLEMENT_PENDING)).isEqualTo(CustomerStatus.COMPLETED);
        assertThat(CustomerStatus.of(SETTLED)).isEqualTo(CustomerStatus.COMPLETED);
        assertThat(CustomerStatus.of(COMPLETED)).isEqualTo(CustomerStatus.COMPLETED);
    }

    // Settlement is between Kora and the provider: the customer's money already moved.
    @Test
    void should_show_completed_when_only_the_settlement_failed() {
        assertThat(CustomerStatus.of(SETTLEMENT_FAILED)).isEqualTo(CustomerStatus.COMPLETED);
    }

    @Test
    void should_show_failed_when_nothing_moved() {
        assertThat(CustomerStatus.of(FAILED)).isEqualTo(CustomerStatus.FAILED);
        assertThat(CustomerStatus.of(AUTHORIZATION_FAILED)).isEqualTo(CustomerStatus.FAILED);
        assertThat(CustomerStatus.of(CAPTURE_FAILED)).isEqualTo(CustomerStatus.FAILED);
    }

    @Test
    void should_show_refunded_after_a_reversal() {
        assertThat(CustomerStatus.of(REVERSED)).isEqualTo(CustomerStatus.REFUNDED);
    }

    @Test
    void should_list_the_states_behind_each_status_for_filtering() {
        assertThat(CustomerStatus.FAILED.states())
                .containsExactlyInAnyOrder(FAILED, AUTHORIZATION_FAILED, CAPTURE_FAILED);
        assertThat(CustomerStatus.REFUNDED.states()).containsExactly(REVERSED);
    }

    // A state added to TransactionState fails here until someone decides what the customer sees.
    @Test
    void should_give_every_declared_state_exactly_one_status() {
        for (TransactionState state : declaredStates()) {
            long owners = Arrays.stream(CustomerStatus.values())
                    .filter(status -> status.states().contains(state))
                    .count();
            assertThat(owners).describedAs(state.name()).isEqualTo(1);
        }
    }

    private static List<TransactionState> declaredStates() {
        return Arrays.stream(TransactionState.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> field.getType() == TransactionState.class)
                .map(CustomerStatusTest::read)
                .toList();
    }

    private static TransactionState read(Field field) {
        try {
            return (TransactionState) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
