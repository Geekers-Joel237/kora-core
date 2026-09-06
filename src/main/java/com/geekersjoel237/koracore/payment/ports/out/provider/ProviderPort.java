package com.geekersjoel237.koracore.payment.ports.out.provider;

import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.ProviderOperationType;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.payment.domain.vo.AuthorizationResult;
import com.geekersjoel237.koracore.payment.domain.vo.CaptureResult;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.auth.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.payment.domain.vo.ReverseResult;

public interface ProviderPort {

    AuthorizationResult authorize(Amount amount,
                                  PaymentMethod paymentMethod,
                                  Id correlationId,
                                  ProviderOperationType operationType,
                                  PhoneNumber customerPhone);

    CaptureResult capture(String authorizationReference, Id correlationId);

    ReverseResult reverse(String reference, Amount amount, Id correlationId);
}