package com.geekersjoel237.koracore.domain.port;

import com.geekersjoel237.koracore.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.domain.enums.ProviderOperationType;
import com.geekersjoel237.koracore.domain.vo.*;

public interface ProviderPort {

    AuthorizationResult authorize(Amount amount,
                                  PaymentMethod paymentMethod,
                                  Id correlationId,
                                  ProviderOperationType operationType,
                                  PhoneNumber customerPhone);

    CaptureResult capture(String authorizationReference, Id correlationId);

    ReverseResult reverse(String reference, Amount amount, Id correlationId);
}