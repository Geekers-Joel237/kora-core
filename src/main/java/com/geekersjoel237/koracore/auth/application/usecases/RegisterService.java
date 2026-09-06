package com.geekersjoel237.koracore.auth.application.usecases;

import com.geekersjoel237.koracore.auth.domain.enums.OtpPurpose;
import com.geekersjoel237.koracore.auth.application.command.RegisterCommand;
import com.geekersjoel237.koracore.auth.ports.in.RegisterCommandHandler;
import com.geekersjoel237.koracore.auth.ports.out.otp.OtpChallenge;
import com.geekersjoel237.koracore.auth.domain.enums.Role;
import com.geekersjoel237.koracore.auth.domain.exception.DuplicateEmailException;
import com.geekersjoel237.koracore.auth.domain.model.Customer;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.ports.out.security.CustomerPinEncoder;
import com.geekersjoel237.koracore.auth.ports.out.repository.CustomerRepository;
import com.geekersjoel237.koracore.auth.ports.out.repository.UserRepository;
import com.geekersjoel237.koracore.auth.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.ports.out.repository.AccountRepository;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;
import com.geekersjoel237.koracore.shared.domain.vo.Id;


public final class RegisterService implements RegisterCommandHandler {

    private final TransactionBoundary boundary;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final CustomerPinEncoder pinEncoder;
    private final OtpChallenge otpChallenge;

    public RegisterService(TransactionBoundary boundary, UserRepository userRepository,
                           CustomerRepository customerRepository, AccountRepository accountRepository,
                           CustomerPinEncoder pinEncoder, OtpChallenge otpChallenge) {
        this.boundary = boundary;
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.pinEncoder = pinEncoder;
        this.otpChallenge = otpChallenge;
    }

    @Override
    public Void execute(RegisterCommand cmd) {
        return boundary.execute(() -> {
            if (customerRepository.existsByEmail(cmd.email()))
                throw new DuplicateEmailException("Email already registered: " + cmd.email());

            User user = User.create(Id.generate(), cmd.fullName(), cmd.email(), Role.CUSTOMER);
            userRepository.save(user);

            Customer customer = Customer.create(
                    user, PhoneNumber.of(cmd.phonePrefix(), cmd.phoneNumber()),
                    cmd.pin(), pinEncoder);
            customerRepository.save(customer);

            accountRepository.save(Account.createCustomerAccount(
                    Id.generate(), customer.snapshot().customerId()));

            otpChallenge.issue(cmd.email(), OtpPurpose.REGISTRATION);
            return null;
        });
    }
}
