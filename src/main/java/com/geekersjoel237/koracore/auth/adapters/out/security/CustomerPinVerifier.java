package com.geekersjoel237.koracore.auth.adapters.out.security;

import com.geekersjoel237.koracore.payment.ports.out.security.PinVerifier;
import com.geekersjoel237.koracore.auth.domain.exception.CustomerNotFoundException;
import com.geekersjoel237.koracore.auth.domain.exception.PinValidationException;
import com.geekersjoel237.koracore.auth.domain.model.Customer;
import com.geekersjoel237.koracore.auth.ports.out.security.CustomerPinEncoder;
import com.geekersjoel237.koracore.auth.ports.out.repository.CustomerRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;


public class CustomerPinVerifier implements PinVerifier {

    private final CustomerRepository customerRepository;
    private final CustomerPinEncoder pinEncoder;

    public CustomerPinVerifier(CustomerRepository customerRepository,
                               CustomerPinEncoder pinEncoder) {
        this.customerRepository = customerRepository;
        this.pinEncoder = pinEncoder;
    }

    @Override
    public void verify(Id customerId, Pin pin) {
        if (pin == null)
            throw new IllegalArgumentException("Pin cannot be null");

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Customer not found: " + customerId.value()));

        if (!customer.matchesPin(pin, pinEncoder))
            throw new PinValidationException("Invalid PIN");
    }
}
