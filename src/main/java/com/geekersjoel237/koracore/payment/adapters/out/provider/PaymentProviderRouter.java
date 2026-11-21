package com.geekersjoel237.koracore.payment.adapters.out.provider;

import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.ProviderOperationType;
import com.geekersjoel237.koracore.payment.domain.vo.AuthorizationResult;
import com.geekersjoel237.koracore.payment.domain.vo.CaptureResult;
import com.geekersjoel237.koracore.payment.domain.vo.ReverseResult;
import com.geekersjoel237.koracore.payment.ports.out.provider.PaymentProviderPort;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.PhoneNumber;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentProviderRouter implements PaymentProviderPort {

    private final Map<PaymentMethod, PaymentMethodProvider> providers = new EnumMap<>(PaymentMethod.class);

    public PaymentProviderRouter(List<PaymentMethodProvider> providers) {
        for (PaymentMethodProvider provider : providers) {
            PaymentMethodProvider previous = this.providers.putIfAbsent(provider.method(), provider);
            if (previous != null)
                throw new IllegalStateException("Two providers serve " + provider.method() + ": "
                        + previous.getClass().getSimpleName() + " and " + provider.getClass().getSimpleName());
        }
    }

    @Override
    public AuthorizationResult authorize(Amount amount, PaymentMethod paymentMethod, Id correlationId,
                                         ProviderOperationType operationType, PhoneNumber customerPhone) {
        return providerOf(paymentMethod).authorize(amount, correlationId, operationType, customerPhone);
    }

    @Override
    public CaptureResult capture(PaymentMethod paymentMethod, String authorizationReference, Id correlationId) {
        return providerOf(paymentMethod).capture(authorizationReference, correlationId);
    }

    @Override
    public ReverseResult reverse(PaymentMethod paymentMethod, String reference, Amount amount, Id correlationId) {
        return providerOf(paymentMethod).reverse(reference, amount, correlationId);
    }

    private PaymentMethodProvider providerOf(PaymentMethod paymentMethod) {
        PaymentMethodProvider provider = providers.get(paymentMethod);
        if (provider == null)
            throw new IllegalArgumentException("No provider serves " + paymentMethod);
        return provider;
    }
}
