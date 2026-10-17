package com.geekersjoel237.koracore.payment.unit.domain.hold;

import com.geekersjoel237.koracore.payment.domain.enums.HoldStatus;
import com.geekersjoel237.koracore.payment.domain.exception.HoldNotActiveException;
import com.geekersjoel237.koracore.payment.domain.model.FundsHold;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundsHoldTest {

    private static final Id TX = new Id("tx-001");
    private static final Id WALLET = new Id("wallet-001");
    private static final Amount AMOUNT = Amount.of(BigDecimal.valueOf(5000), "XAF");

    private static FundsHold placed() {
        return FundsHold.place(TX, WALLET, AMOUNT);
    }

    @Test
    void a_new_hold_is_active_and_unresolved() {
        FundsHold.Snapshot snapshot = placed().snapshot();

        assertThat(snapshot.status()).isEqualTo(HoldStatus.ACTIVE);
        assertThat(snapshot.transactionId()).isEqualTo(TX);
        assertThat(snapshot.accountId()).isEqualTo(WALLET);
        assertThat(snapshot.amount()).isEqualTo(AMOUNT);
        assertThat(snapshot.resolvedAt()).isNull();
    }

    @Test
    void a_hold_must_be_for_a_positive_amount() {
        assertThatThrownBy(() -> FundsHold.place(TX, WALLET, Amount.of(BigDecimal.ZERO, "XAF")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consuming_resolves_the_hold() {
        FundsHold hold = placed();

        hold.consume();

        assertThat(hold.snapshot().status()).isEqualTo(HoldStatus.CONSUMED);
        assertThat(hold.snapshot().resolvedAt()).isNotNull();
    }

    @Test
    void releasing_resolves_the_hold() {
        FundsHold hold = placed();

        hold.release();

        assertThat(hold.snapshot().status()).isEqualTo(HoldStatus.RELEASED);
        assertThat(hold.snapshot().resolvedAt()).isNotNull();
    }

    @Test
    void a_consumed_hold_cannot_be_released() {
        FundsHold hold = placed();
        hold.consume();

        assertThatThrownBy(hold::release).isInstanceOf(HoldNotActiveException.class);
    }

    @Test
    void a_released_hold_cannot_be_consumed() {
        FundsHold hold = placed();
        hold.release();

        assertThatThrownBy(hold::consume).isInstanceOf(HoldNotActiveException.class);
    }

    @Test
    void a_snapshot_round_trips() {
        FundsHold hold = placed();
        hold.consume();

        assertThat(FundsHold.createFromSnapshot(hold.snapshot()).snapshot()).isEqualTo(hold.snapshot());
    }
}
