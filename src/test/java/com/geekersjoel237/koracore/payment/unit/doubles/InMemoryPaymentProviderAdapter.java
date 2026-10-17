package com.geekersjoel237.koracore.payment.unit.doubles;

import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.ProviderOperationType;
import com.geekersjoel237.koracore.payment.domain.exception.PaymentProviderException;
import com.geekersjoel237.koracore.payment.domain.exception.PaymentTimeoutProviderException;
import com.geekersjoel237.koracore.payment.domain.exception.PaymentTimeoutProviderException;
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
    private String lastAuthorizationReference = null;

    public InMemoryPaymentProviderAdapter(Behavior behavior) {
        this.behavior = behavior;
    }

    public void setBehavior(Behavior behavior) {
        this.behavior = behavior;
    }

    public int authorizeCalls() { return authorizeCalls; }
    public int captureCalls()   { return captureCalls; }
    public String lastAuthorizationReference() { return lastAuthorizationReference; }

    public ProviderOperationType getLastOperationType() { return lastOperationType; }
    public PhoneNumber getLastCustomerPhone()           { return lastCustomerPhone; }

    public void reset() {
        this.lastOperationType = null;
        this.lastCustomerPhone = null;
        this.authorizeCalls = 0;
        this.captureCalls = 0;
        this.lastAuthorizationReference = null;
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
        if (behavior == Behavior.TIMEOUT_ON_AUTHORIZE)
            throw new PaymentTimeoutProviderException("Provider simulated authorization timeout");

        lastAuthorizationReference = UUID.randomUUID().toString();
        return new AuthorizationResult(
                lastAuthorizationReference,
                Instant.now().plus(15, ChronoUnit.MINUTES),
                true);
    }

    @Override
    public CaptureResult capture(String authorizationReference, Id correlationId) {
        this.captureCalls++;
        if (behavior == Behavior.FAIL_ON_CAPTURE)
            throw new PaymentProviderException("Provider simulated capture failure");
        if (behavior == Behavior.TIMEOUT_ON_CAPTURE)
            throw new PaymentTimeoutProviderException("Provider simulated capture timeout");
        return new CaptureResult(UUID.randomUUID().toString(), true);
    }

    @Override
    public ReverseResult reverse(String reference, Amount amount, Id correlationId) {
        if (behavior == Behavior.TIMEOUT_ON_REVERSE)
            throw new PaymentTimeoutProviderException("Provider simulated reversal timeout");
        return new ReverseResult(UUID.randomUUID().toString(), true);
    }

    public enum Behavior {
        SUCCESS,
        FAIL_ON_AUTHORIZE,
        FAIL_ON_CAPTURE,
        TIMEOUT_ON_AUTHORIZE,
        TIMEOUT_ON_CAPTURE,
        TIMEOUT_ON_REVERSE,
        /** Backward-compatible alias for {@link #FAIL_ON_AUTHORIZE}. */
        FAIL
    }
}