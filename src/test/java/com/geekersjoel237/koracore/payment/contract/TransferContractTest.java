package com.geekersjoel237.koracore.payment.contract;

import com.geekersjoel237.koracore.auth.domain.exception.PinValidationException;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.transfer.TransferAction;
import com.geekersjoel237.koracore.payment.application.command.TransferCommand;
import com.geekersjoel237.koracore.payment.application.result.PaymentResult;
import com.geekersjoel237.koracore.payment.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.payment.domain.exception.InsufficientFundsException;
import com.geekersjoel237.koracore.payment.domain.exception.SelfTransferException;
import com.geekersjoel237.koracore.shared.application.cqrs.CommandReplayedException;
import com.geekersjoel237.koracore.shared.application.transaction.RetryExhaustedException;
import com.geekersjoel237.koracore.shared.contract.ContractTest;
import com.geekersjoel237.koracore.shared.contract.ContractTokens;
import com.geekersjoel237.koracore.shared.contract.ProgrammableCommandBus;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.PhoneNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.math.BigDecimal;

import static com.geekersjoel237.koracore.shared.contract.ContractRequests.CORRELATION_ID;
import static com.geekersjoel237.koracore.shared.contract.ContractRequests.post;
import static com.geekersjoel237.koracore.shared.contract.ContractSnapshot.assertMatches;
import static org.assertj.core.api.Assertions.assertThat;

@ContractTest(TransferAction.class)
class TransferContractTest {

    private static final String VALID = """
            {"rawPin": "1234", "amount": 5000, "currency": "XAF",
             "toPhonePrefix": "+237", "toPhoneNumber": "690000001"}
            """;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ProgrammableCommandBus bus;

    @Autowired
    private ContractTokens tokens;

    @BeforeEach
    void reset() {
        bus.reset();
    }

    @Test
    void a_completed_transfer_answers_200_with_the_transaction() {
        bus.answer(TransferCommand.class, command -> result());

        assertMatches("payment/transfer/200-completed", transfer(tokens.customer(), VALID));
    }

    @Test
    void the_request_reaches_the_use_case_with_the_recipient_s_phone_number() {
        bus.answer(TransferCommand.class, command -> result());

        transfer(tokens.customer(), VALID);

        TransferCommand command = bus.lastDispatched(TransferCommand.class);
        assertThat(command.customerId()).isEqualTo(new Id(ContractTokens.CUSTOMER_ID));
        assertThat(command.correlationId()).isEqualTo(new Id(CORRELATION_ID));
        assertThat(command.amount()).isEqualTo(new Amount(new BigDecimal("5000"), "XAF"));
        assertThat(command.recipient()).isEqualTo(PhoneNumber.of("+237", "690000001"));
    }

    @Test
    void a_recipient_number_with_letters_is_a_400_naming_the_field() {
        assertMatches("payment/transfer/400-recipient-number-invalid", transfer(tokens.customer(), """
                {"rawPin": "1234", "amount": 5000, "currency": "XAF",
                 "toPhonePrefix": "+237", "toPhoneNumber": "69000000A"}
                """));
    }

    @Test
    void a_recipient_prefix_without_plus_is_a_400_naming_the_field() {
        assertMatches("payment/transfer/400-recipient-prefix-invalid", transfer(tokens.customer(), """
                {"rawPin": "1234", "amount": 5000, "currency": "XAF",
                 "toPhonePrefix": "237", "toPhoneNumber": "690000001"}
                """));
    }

    @Test
    void no_token_is_a_401_in_the_security_format() {
        assertMatches("payment/transfer/401-no-token", transfer(null, VALID));
    }

    @Test
    void a_wrong_pin_is_a_401_in_the_problem_format() {
        bus.fail(TransferCommand.class, new PinValidationException("Invalid PIN"));

        assertMatches("payment/transfer/401-wrong-pin", transfer(tokens.customer(), VALID));
    }

    @Test
    void an_operator_cannot_transfer_from_a_customer_wallet() {
        assertMatches("payment/transfer/403-admin-token", transfer(tokens.admin(), VALID));
    }

    @Test
    void an_unknown_recipient_is_a_404_not_a_422() {
        bus.fail(TransferCommand.class, new AccountNotFoundException("No wallet for +237690000001"));

        assertMatches("payment/transfer/404-recipient-not-found", transfer(tokens.customer(), VALID));
    }

    @Test
    void a_replayed_command_is_a_409() {
        bus.fail(TransferCommand.class, new CommandReplayedException(new Id(CORRELATION_ID)));

        assertMatches("payment/transfer/409-replayed", transfer(tokens.customer(), VALID));
    }

    @Test
    void insufficient_funds_is_a_422() {
        bus.fail(TransferCommand.class, new InsufficientFundsException("Insufficient funds"));

        assertMatches("payment/transfer/422-insufficient-funds", transfer(tokens.customer(), VALID));
    }

    @Test
    void a_transfer_to_oneself_is_a_422() {
        bus.fail(TransferCommand.class, new SelfTransferException("Cannot transfer to your own wallet"));

        assertMatches("payment/transfer/422-self-transfer", transfer(tokens.customer(), VALID));
    }

    @Test
    void exhausted_retries_are_a_503() {
        bus.fail(TransferCommand.class, new RetryExhaustedException("Retries exhausted", new RuntimeException()));

        assertMatches("payment/transfer/503-retries-exhausted", transfer(tokens.customer(), VALID));
    }

    private MvcTestResult transfer(String token, String body) {
        return post(mvc, "/payments/transfer", token, body);
    }

    private static PaymentResult result() {
        return new PaymentResult(
                "66666666-6666-6666-6666-666666666666",
                "TRX-20261119-C5F13E4F",
                "COMPLETED",
                new Amount(new BigDecimal("5000"), "XAF"));
    }
}
