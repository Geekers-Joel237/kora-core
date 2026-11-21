package com.geekersjoel237.koracore.auth.unit.application;

import com.geekersjoel237.koracore.auth.adapters.out.otp.MailedOtpChallenge;
import com.geekersjoel237.koracore.auth.adapters.out.security.BCryptCustomerPinEncoder;
import com.geekersjoel237.koracore.auth.adapters.out.security.CustomerPinVerifier;
import com.geekersjoel237.koracore.auth.application.command.login.LoginCommand;
import com.geekersjoel237.koracore.auth.application.command.login.LoginService;
import com.geekersjoel237.koracore.auth.application.command.logout.LogoutCommand;
import com.geekersjoel237.koracore.auth.application.command.register.RegisterCommand;
import com.geekersjoel237.koracore.auth.application.command.register.RegisterService;
import com.geekersjoel237.koracore.auth.application.command.verifyOtp.VerifyOtpCommand;
import com.geekersjoel237.koracore.auth.application.command.verifyOtp.VerifyOtpService;
import com.geekersjoel237.koracore.auth.domain.enums.OtpPurpose;
import com.geekersjoel237.koracore.auth.domain.exception.CustomerNotFoundException;
import com.geekersjoel237.koracore.auth.domain.exception.DuplicateEmailException;
import com.geekersjoel237.koracore.auth.domain.exception.InvalidOtpException;
import com.geekersjoel237.koracore.auth.domain.exception.MailDeliveryException;
import com.geekersjoel237.koracore.auth.domain.exception.PinValidationException;
import com.geekersjoel237.koracore.auth.domain.vo.OtpCode;
import com.geekersjoel237.koracore.auth.domain.vo.RefreshToken;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.auth.ports.in.LoginCommandHandler;
import com.geekersjoel237.koracore.auth.ports.in.RegisterCommandHandler;
import com.geekersjoel237.koracore.auth.ports.in.VerifyOtpCommandHandler;
import com.geekersjoel237.koracore.auth.ports.out.otp.OtpChallenge;
import com.geekersjoel237.koracore.auth.ports.out.security.CustomerPinEncoder;
import com.geekersjoel237.koracore.auth.unit.doubles.InMemoryCustomerRepository;
import com.geekersjoel237.koracore.auth.unit.doubles.InMemoryTokenIssuer;
import com.geekersjoel237.koracore.auth.unit.doubles.InMemoryUserRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryAccountRepository;
import com.geekersjoel237.koracore.shared.adapters.out.store.InMemoryExpiringStore;
import com.geekersjoel237.koracore.shared.domain.exception.MailProviderException;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;
import com.geekersjoel237.koracore.shared.ports.out.mail.Mail;
import com.geekersjoel237.koracore.shared.ports.out.mail.MailPort;
import com.geekersjoel237.koracore.shared.unit.doubles.DirectTransactionBoundary;
import com.geekersjoel237.koracore.shared.unit.doubles.FailingMailPort;
import com.geekersjoel237.koracore.shared.unit.doubles.InMemoryMailPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthUseCaseTest {

    private static final String EMAIL = "joel@koracore.com";
    private static final Pin PIN = Pin.of("123456");
    private static final Pattern SIX_DIGITS = Pattern.compile("\\b(\\d{6})\\b");

    private final CustomerPinEncoder pinEncoder = new BCryptCustomerPinEncoder();

    private InMemoryUserRepository users;
    private InMemoryCustomerRepository customers;
    private InMemoryAccountRepository accounts;
    private InMemoryMailPort mail;
    private InMemoryTokenIssuer tokenIssuer;
    private OtpChallenge otpChallenge;
    private RegisterCommandHandler register;
    private VerifyOtpCommandHandler verifyOtp;
    private LoginCommandHandler login;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        customers = new InMemoryCustomerRepository();
        accounts = new InMemoryAccountRepository();
        mail = new InMemoryMailPort();
        tokenIssuer = new InMemoryTokenIssuer(Clock.systemUTC());
        otpChallenge = otpChallengeSendingTo(mail);
        register = registerSendingTo(otpChallenge);
        verifyOtp = new VerifyOtpService(new DirectTransactionBoundary(), customers, otpChallenge, tokenIssuer);
        login = new LoginService(new DirectTransactionBoundary(), customers,
                new CustomerPinVerifier(customers, pinEncoder), otpChallenge);
    }

    private static OtpChallenge otpChallengeSendingTo(MailPort port) {
        return new MailedOtpChallenge(new InMemoryExpiringStore<>(Clock.systemUTC()), port, Duration.ofMinutes(5));
    }

    private RegisterCommandHandler registerSendingTo(OtpChallenge challenge) {
        return new RegisterService(new DirectTransactionBoundary(), users, customers, accounts, pinEncoder, challenge);
    }

    private static RegisterCommand registration(String fullName, String email) {
        return new RegisterCommand(Id.generate(), fullName, email, "+237", "699887766", PIN);
    }

    private void registerJoel() {
        register.execute(registration("Joel Geekers", EMAIL));
    }

    private OtpCode codeMailedTo(String email) {
        Mail last = mail.lastMailTo(email).orElseThrow(() -> new AssertionError("No mail was sent to " + email));
        Matcher matcher = SIX_DIGITS.matcher(last.body());
        if (!matcher.find()) throw new AssertionError("No six-digit code in: " + last.body());
        return OtpCode.of(matcher.group(1));
    }

    private OtpCode aCodeThatIsNot(OtpCode code) {
        return OtpCode.of(code.value().equals("000000") ? "111111" : "000000");
    }

    @Nested
    class Registration {

        @Test
        void a_registration_opens_a_customer_with_an_empty_wallet_and_mails_a_code() {
            registerJoel();

            var customer = customers.findByEmail(EMAIL).orElseThrow();
            assertThat(accounts.findByCustomerId(customer.snapshot().customerId())).isPresent();
            assertThat(accounts.findByCustomerId(customer.snapshot().customerId()).orElseThrow()
                    .snapshot().balance().solde().value()).isZero();
            assertThat(mail.sent()).hasSize(1);
            assertThat(codeMailedTo(EMAIL).value()).hasSize(6);
        }

        @Test
        void an_email_registers_once() {
            registerJoel();

            assertThatThrownBy(() -> register.execute(registration("Someone Else", EMAIL)))
                    .isInstanceOf(DuplicateEmailException.class)
                    .hasMessageContaining(EMAIL);
            assertThat(mail.sent()).hasSize(1);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void a_registration_needs_a_name(String fullName) {
            assertThatThrownBy(() -> register.execute(registration(fullName, EMAIL)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fullName");
            assertThat(mail.sent()).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"not-an-email", "joel@", "@koracore.com"})
        void a_registration_needs_a_valid_email(String email) {
            assertThatThrownBy(() -> register.execute(registration("Joel Geekers", email)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("email");
            assertThat(mail.sent()).isEmpty();
        }

        @Test
        void a_registration_whose_code_cannot_be_mailed_fails_after_retrying() {
            var outage = new FailingMailPort();
            var registerDuringOutage = registerSendingTo(otpChallengeSendingTo(outage));

            assertThatThrownBy(() -> registerDuringOutage.execute(registration("Joel Geekers", EMAIL)))
                    .isInstanceOf(MailDeliveryException.class)
                    .hasCauseInstanceOf(MailProviderException.class);
            assertThat(outage.attempts()).isGreaterThan(1);
        }
    }

    @Nested
    class CodeVerification {

        @Test
        void the_registration_code_opens_a_session_for_the_new_customer() {
            registerJoel();

            Tokens tokens = verifyOtp.execute(new VerifyOtpCommand(Id.generate(), EMAIL, codeMailedTo(EMAIL)));

            var customerId = customers.findByEmail(EMAIL).orElseThrow().snapshot().customerId();
            assertThat(tokenIssuer.holderOf(tokens.refreshToken().token())).isEqualTo(customerId);
        }

        @Test
        void a_wrong_code_opens_no_session() {
            registerJoel();
            OtpCode wrong = aCodeThatIsNot(codeMailedTo(EMAIL));

            assertThatThrownBy(() -> verifyOtp.execute(new VerifyOtpCommand(Id.generate(), EMAIL, wrong)))
                    .isInstanceOf(InvalidOtpException.class);
        }

        @Test
        void a_code_mailed_to_an_address_without_a_customer_opens_no_session() {
            String stranger = "stranger@koracore.com";
            otpChallenge.issue(stranger, OtpPurpose.LOGIN);

            assertThatThrownBy(() -> verifyOtp.execute(
                    new VerifyOtpCommand(Id.generate(), stranger, codeMailedTo(stranger))))
                    .isInstanceOf(CustomerNotFoundException.class)
                    .hasMessageContaining(stranger);
        }

        @Test
        void issued_tokens_never_print_their_value() {
            registerJoel();
            Tokens tokens = verifyOtp.execute(new VerifyOtpCommand(Id.generate(), EMAIL, codeMailedTo(EMAIL)));

            assertThat(tokens.toString())
                    .doesNotContain(tokens.accessToken().token().value())
                    .doesNotContain(tokens.refreshToken().token().value());
            assertThat(new LogoutCommand(Id.generate(), tokens.refreshToken().token()).toString())
                    .doesNotContain(tokens.refreshToken().token().value());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void a_session_is_never_named_by_a_blank_refresh_token(String value) {
            assertThatThrownBy(() -> new LogoutCommand(Id.generate(), RefreshToken.of(value)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Login {

        @BeforeEach
        void registerAndForgetTheRegistrationMail() {
            registerJoel();
            mail.reset();
        }

        @Test
        void the_right_pin_mails_a_login_code_that_opens_a_session() {
            login.execute(new LoginCommand(Id.generate(), EMAIL, PIN));

            Tokens tokens = verifyOtp.execute(new VerifyOtpCommand(Id.generate(), EMAIL, codeMailedTo(EMAIL)));

            assertThat(tokens.accessToken().token().value()).isNotBlank();
        }

        @Test
        void a_wrong_pin_mails_nothing() {
            assertThatThrownBy(() -> login.execute(new LoginCommand(Id.generate(), EMAIL, Pin.of("654321"))))
                    .isInstanceOf(PinValidationException.class);
            assertThat(mail.sent()).isEmpty();
        }

        @Test
        void an_unknown_email_mails_nothing() {
            assertThatThrownBy(() -> login.execute(
                    new LoginCommand(Id.generate(), "ghost@koracore.com", PIN)))
                    .isInstanceOf(CustomerNotFoundException.class);
            assertThat(mail.sent()).isEmpty();
        }
    }
}
