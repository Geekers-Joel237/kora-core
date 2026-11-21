package com.geekersjoel237.koracore.payment.unit.adapters.provider;

import com.geekersjoel237.koracore.payment.adapters.out.provider.CardProvider;
import com.geekersjoel237.koracore.payment.adapters.out.provider.MobileMoneyPaymentProviderAdapter;
import com.geekersjoel237.koracore.payment.adapters.out.provider.MomoProvider;
import com.geekersjoel237.koracore.payment.adapters.out.provider.OrangeMoneyProvider;
import com.geekersjoel237.koracore.payment.adapters.out.provider.PaymentMethodProvider;
import com.geekersjoel237.koracore.payment.adapters.out.provider.PaymentProviderRouter;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.ProviderOperationType;
import com.geekersjoel237.koracore.payment.domain.vo.AuthorizationResult;
import com.geekersjoel237.koracore.payment.domain.vo.CaptureResult;
import com.geekersjoel237.koracore.payment.domain.vo.ReverseResult;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.PhoneNumber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentProviderRouterTest {

    private static final Amount AMOUNT = Amount.of(BigDecimal.valueOf(5_000), "XAF");
    private static final PhoneNumber PHONE = PhoneNumber.of("+237", "699887766");

    private final RecordingProvider orangeMoney = new RecordingProvider(PaymentMethod.ORANGE_MONEY);
    private final RecordingProvider momo = new RecordingProvider(PaymentMethod.MOBILE_MONEY);
    private final PaymentProviderRouter router = new PaymentProviderRouter(List.of(orangeMoney, momo));

    @Test
    void every_step_of_a_payment_goes_to_the_provider_of_its_method() {
        router.authorize(AMOUNT, PaymentMethod.MOBILE_MONEY, Id.generate(),
                ProviderOperationType.COLLECTION, PHONE);
        router.capture(PaymentMethod.MOBILE_MONEY, "auth-ref", Id.generate());
        router.reverse(PaymentMethod.MOBILE_MONEY, "auth-ref", AMOUNT, Id.generate());

        assertThat(momo.calls).containsExactly("authorize", "capture", "reverse");
        assertThat(orangeMoney.calls).isEmpty();
    }

    @Test
    void a_method_no_provider_serves_is_refused() {
        assertThatThrownBy(() -> router.authorize(AMOUNT, PaymentMethod.CARD, Id.generate(),
                ProviderOperationType.COLLECTION, PHONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CARD");
    }

    @Test
    void two_providers_cannot_serve_the_same_method() {
        assertThatThrownBy(() -> new PaymentProviderRouter(
                List.of(orangeMoney, new RecordingProvider(PaymentMethod.ORANGE_MONEY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ORANGE_MONEY");
    }

    @ParameterizedTest
    @EnumSource(value = PaymentMethod.class, names = {"CARD", "ORANGE_MONEY", "MOBILE_MONEY"})
    void every_method_paid_outside_the_wallet_has_its_provider(PaymentMethod method) {
        var simulator = new MobileMoneyPaymentProviderAdapter("SUCCESS", false, 0, 0, 0);
        var wired = new PaymentProviderRouter(List.of(
                new OrangeMoneyProvider(simulator), new MomoProvider(simulator), new CardProvider(simulator)));

        assertThatNoException().isThrownBy(() -> {
            AuthorizationResult authorization = wired.authorize(AMOUNT, method, Id.generate(),
                    ProviderOperationType.COLLECTION, PHONE);
            wired.capture(method, authorization.providerReference(), Id.generate());
            wired.reverse(method, authorization.providerReference(), AMOUNT, Id.generate());
        });
    }

    @Test
    void the_wallet_moves_money_without_any_provider() {
        var simulator = new MobileMoneyPaymentProviderAdapter("SUCCESS", false, 0, 0, 0);
        var wired = new PaymentProviderRouter(List.of(
                new OrangeMoneyProvider(simulator), new MomoProvider(simulator), new CardProvider(simulator)));

        assertThatThrownBy(() -> wired.capture(PaymentMethod.WALLET, "ref", Id.generate()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class RecordingProvider implements PaymentMethodProvider {

        private final PaymentMethod method;
        private final List<String> calls = new ArrayList<>();

        private RecordingProvider(PaymentMethod method) {
            this.method = method;
        }

        @Override
        public PaymentMethod method() {
            return method;
        }

        @Override
        public AuthorizationResult authorize(Amount amount, Id correlationId,
                                             ProviderOperationType operationType, PhoneNumber customerPhone) {
            calls.add("authorize");
            return new AuthorizationResult("auth-ref", Instant.now(), true);
        }

        @Override
        public CaptureResult capture(String authorizationReference, Id correlationId) {
            calls.add("capture");
            return new CaptureResult("capture-ref", true);
        }

        @Override
        public ReverseResult reverse(String reference, Amount amount, Id correlationId) {
            calls.add("reverse");
            return new ReverseResult("reverse-ref", true);
        }
    }
}
