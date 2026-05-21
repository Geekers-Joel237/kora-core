package com.geekersjoel237.koracore.domain.port;

import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.AuthorizationResult;
import com.geekersjoel237.koracore.domain.vo.CaptureResult;
import com.geekersjoel237.koracore.domain.vo.ReverseResult;

public interface ProviderPort {


    AuthorizationResult authorize(Amount amount, String paymentMethod, String correlationId);

    CaptureResult capture(String authorizationReference, String correlationId);

    ReverseResult reverse(String reference, Amount amount, String correlationId);
}