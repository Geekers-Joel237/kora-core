package com.geekersjoel237.koracore.auth.config;

import com.geekersjoel237.koracore.auth.adapters.out.otp.MailedOtpChallenge;
import com.geekersjoel237.koracore.auth.adapters.out.security.CustomerPinVerifier;
import com.geekersjoel237.koracore.auth.application.usecases.LoginService;
import com.geekersjoel237.koracore.auth.application.usecases.RefreshTokensService;
import com.geekersjoel237.koracore.auth.application.usecases.RegisterService;
import com.geekersjoel237.koracore.auth.application.usecases.VerifyOtpService;
import com.geekersjoel237.koracore.auth.ports.in.LoginCommandHandler;
import com.geekersjoel237.koracore.auth.ports.in.RefreshTokensCommandHandler;
import com.geekersjoel237.koracore.auth.ports.in.RegisterCommandHandler;
import com.geekersjoel237.koracore.auth.ports.in.VerifyOtpCommandHandler;
import com.geekersjoel237.koracore.shared.ports.out.mail.MailPort;
import com.geekersjoel237.koracore.auth.ports.out.otp.OtpChallenge;
import com.geekersjoel237.koracore.auth.domain.vo.OtpCode;
import com.geekersjoel237.koracore.shared.adapters.out.store.InMemoryExpiringStore;
import com.geekersjoel237.koracore.shared.ports.out.store.ExpiringStore;
import com.geekersjoel237.koracore.auth.ports.out.repository.CustomerRepository;
import com.geekersjoel237.koracore.auth.ports.out.repository.UserRepository;
import com.geekersjoel237.koracore.auth.ports.out.security.CustomerPinEncoder;
import com.geekersjoel237.koracore.auth.ports.out.security.TokenIssuer;
import com.geekersjoel237.koracore.payment.ports.out.repository.AccountRepository;
import com.geekersjoel237.koracore.payment.ports.out.security.PinVerifier;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;


@Configuration
public class AuthUseCaseConfiguration {


    @Bean
    PinVerifier pinVerifier(CustomerRepository customers, CustomerPinEncoder pinEncoder) {
        return new CustomerPinVerifier(customers, pinEncoder);
    }

    /**
     * Declared here rather than scanned, because the store is generic and only this
     * module knows what it holds. In-memory today; a Redis adapter replaces this line
     * and nothing else.
     */
    @Bean
    ExpiringStore<OtpCode> otpCodes(Clock clock) {
        return new InMemoryExpiringStore<>(clock);
    }

    @Bean
    OtpChallenge otpChallenge(ExpiringStore<OtpCode> otpCodes, MailPort mailPort,
                              SecurityProperties securityProperties) {
        Duration otpValidity = Duration.ofMinutes(securityProperties.otp().expirationMinutes());
        return new MailedOtpChallenge(otpCodes, mailPort, otpValidity);
    }

    @Bean
    RegisterCommandHandler registerCommandHandler(TransactionBoundary boundary, UserRepository users,
                                                  CustomerRepository customers, AccountRepository accounts,
                                                  CustomerPinEncoder pinEncoder, OtpChallenge otpChallenge) {
        return new RegisterService(boundary, users, customers, accounts, pinEncoder, otpChallenge);
    }

    @Bean
    LoginCommandHandler loginCommandHandler(TransactionBoundary boundary, CustomerRepository customers,
                                            PinVerifier pinVerifier, OtpChallenge otpChallenge) {
        return new LoginService(boundary, customers, pinVerifier, otpChallenge);
    }

    @Bean
    VerifyOtpCommandHandler verifyOtpCommandHandler(TransactionBoundary boundary, CustomerRepository customers,
                                                    OtpChallenge otpChallenge, TokenIssuer tokenIssuer) {
        return new VerifyOtpService(boundary, customers, otpChallenge, tokenIssuer);
    }

    @Bean
    RefreshTokensCommandHandler refreshTokensCommandHandler(TransactionBoundary boundary, UserRepository users,
                                                            TokenIssuer tokenIssuer) {
        return new RefreshTokensService(boundary, users, tokenIssuer);
    }
}
