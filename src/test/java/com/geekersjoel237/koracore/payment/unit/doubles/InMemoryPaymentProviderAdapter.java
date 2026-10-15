package com.geekersjoel237.koracore.payment.unit.doubles;

import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.ProviderOperationType;
import com.geekersjoel237.koracore.payment.domain.exception.PaymentProviderException;
import com.geekersjoel237.koracore.payment.ports.out.provider.PaymentProviderPort;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.payment.domain.vo.AuthorizationResult;
import com.geekersjoel237.koracore.payment.domain.vo.CaptureResult;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.payment.domain.vo.ReverseResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class InMemoryPaymentProviderAdapter implements PaymentProviderPort {

    private Behavior behavior;
    private ProviderOperationType lastOperationType = null;
    private PhoneNumber lastCustomerPhone = null;
    private int authorizeCalls = 0;
    private int captureCalls = 0;

    public InMemoryPaymentProviderAdapter(Behavior behavior) {
        this.behavior = behavior;
    }

    public void setBehavior(Behavior behavior) {
        this.behavior = behavior;
    }

    public int authorizeCalls() { return authorizeCalls; }
    public int captureCalls()   { return captureCalls; }

    public ProviderOperationType getLastOperationType() { return lastOperationType; }
    public PhoneNumber getLastCustomerPhone()           { return lastCustomerPhone; }

    public void reset() {
        this.lastOperationType = null;
        this.lastCustomerPhone = null;
        this.authorizeCalls = 0;
        this.captureCalls = 0;
    }

    @Override
    public AuthorizationResult authorize(Amount amount,
                                         PaymentMethod paymentMethod,
                                         Id correlationId,
                                         ProviderOperationType operationType,
                                         PhoneNumber customerPhone) {
        this.authorizeCalls++;
        this.lastOperationType = operationType;
        this.lastCustomerPhone = customerPhone;

        if (behavior == Behavior.FAIL || behavior == Behavior.FAIL_ON_AUTHORIZE)
            throw new PaymentProviderException("Provider simulated authorization failure");

        return new AuthorizationResult(
                UUID.randomUUID().toString(),
                Instant.now().plus(15, ChronoUnit.MINUTES),
                true);
    }

    @Override
    public CaptureResult capture(String authorizationReference, Id correlationId) {
        this.captureCalls++;
        if (behavior == Behavior.FAIL_ON_CAPTURE)
            throw new PaymentProviderException("Provider simulated capture failure");
        return new CaptureResult(UUID.randomUUID().toString(), true);
    }

    @Override
    public ReverseResult reverse(String reference, Amount amount, Id correlationId) {
        return new ReverseResult(UUID.randomUUID().toString(), true);
    }

    public enum Behavior {
        SUCCESS,
        FAIL_ON_AUTHORIZE,
        FAIL_ON_CAPTURE,
        /** Backward-compatible alias for {@link #FAIL_ON_AUTHORIZE}. */
        FAIL
    }
}