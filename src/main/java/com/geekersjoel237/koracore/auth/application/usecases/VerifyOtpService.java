package com.geekersjoel237.koracore.auth.application.usecases;

import com.geekersjoel237.koracore.auth.application.command.VerifyOtpCommand;
import com.geekersjoel237.koracore.auth.ports.in.VerifyOtpCommandHandler;
import com.geekersjoel237.koracore.auth.ports.out.otp.OtpChallenge;
import com.geekersjoel237.koracore.auth.ports.out.security.TokenIssuer;
import com.geekersjoel237.koracore.auth.domain.exception.CustomerNotFoundException;
import com.geekersjoel237.koracore.auth.domain.model.Customer;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.ports.out.repository.CustomerRepository;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;

public final class VerifyOtpService implements VerifyOtpCommandHandler {

    private final TransactionBoundary boundary;
    private final CustomerRepository customerRepository;
    private final OtpChallenge otpChallenge;
    private final TokenIssuer tokenIssuer;

    public VerifyOtpService(TransactionBoundary boundary, CustomerRepository customerRepository,
                            OtpChallenge otpChallenge, TokenIssuer tokenIssuer) {
        this.boundary = boundary;
        this.customerRepository = customerRepository;
        this.otpChallenge = otpChallenge;
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    public Tokens execute(VerifyOtpCommand cmd) {
        return boundary.execute(() -> {
            otpChallenge.consume(cmd.email(), cmd.code());

            Customer customer = customerRepository.findByEmail(cmd.email())
                    .orElseThrow(() -> new CustomerNotFoundException(
                            "Customer not found: " + cmd.email()));

            return tokenIssuer.issue(User.createFromSnapshot(customer.snapshot().user()));
        });
    }
}
