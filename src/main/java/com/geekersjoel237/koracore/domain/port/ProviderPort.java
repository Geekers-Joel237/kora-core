package com.geekersjoel237.koracore.domain.port;

import com.geekersjoel237.koracore.domain.vo.Amount;

public interface ProviderPort {
    void credit(Amount amount, String paymentMethod);
    void debit(Amount amount, String paymentMethod);
    void send(Amount amount, String paymentMethod);
}