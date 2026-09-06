package com.geekersjoel237.koracore.auth.application.usecases;

import com.geekersjoel237.koracore.auth.domain.enums.OtpPurpose;
import com.geekersjoel237.koracore.auth.application.command.LoginCommand;
import com.geekersjoel237.koracore.auth.ports.in.LoginCommandHandler;
import com.geekersjoel237.koracore.auth.ports.out.otp.OtpChallenge;
import com.geekersjoel237.koracore.auth.domain.exception.CustomerNotFoundException;
import com.geekersjoel237.koracore.auth.domain.model.Customer;
import com.geekersjoel237.koracore.auth.ports.out.repository.CustomerRepository;
import com.geekersjoel237.koracore.payment.ports.out.security.PinVerifier;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;


public final class LoginService implements LoginCommandHandler {

    private final TransactionBoundary boundary;
    private final CustomerRepository customerRepository;
    private final PinVerifier pinVerifier;
    private final OtpChallenge otpChallenge;

    public LoginService(TransactionBoundary boundary, CustomerRepository customerRepository,
                        PinVerifier pinVerifier, OtpChallenge otpChallenge) {
        this.boundary = boundary;
        this.customerRepository = customerRepository;
        this.pinVerifier = pinVerifier;
        this.otpChallenge = otpChallenge;
    }

    @Override
    public Void execute(LoginCommand cmd) {
        return boundary.execute(() -> {
            Customer customer = customerRepository.findByEmail(cmd.email())
                    .orElseThrow(() -> new CustomerNotFoundException(
                            "Customer not found: " + cmd.email()));

            pinVerifier.verify(customer.snapshot().customerId(), cmd.pin());
            otpChallenge.issue(cmd.email(), OtpPurpose.LOGIN);
            return null;
        });
    }
}
