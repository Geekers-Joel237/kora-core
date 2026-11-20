package com.geekersjoel237.koracore.payment.contract;

import com.geekersjoel237.koracore.auth.domain.exception.PinValidationException;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashIn.CashInAction;
import com.geekersjoel237.koracore.payment.application.command.cashIn.CashInCommand;
import com.geekersjoel237.koracore.payment.application.command.cashIn.CashInResult;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.exception.AccountBlockedException;
import com.geekersjoel237.koracore.payment.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.payment.domain.exception.PaymentProviderException;
import com.geekersjoel237.koracore.shared.application.cqrs.CommandReplayedException;
import com.geekersjoel237.koracore.shared.application.transaction.RetryExhaustedException;
import com.geekersjoel237.koracore.shared.contract.ContractTest;
import com.geekersjoel237.koracore.shared.contract.ContractTokens;
import com.geekersjoel237.koracore.shared.contract.ProgrammableCommandBus;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
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

@ContractTest(CashInAction.class)
class CashInContractTest {

    private static final String VALID = """
            {"rawPin": "1234", "amount": 5000, "currency": "XAF", "paymentMethod": "OM"}
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
    void a_completed_cash_in_answers_200_with_the_transaction() {
        bus.answer(CashInCommand.class, command -> result("COMPLETED"));

        assertMatches("payment/cash-in/200-completed", cashIn(tokens.customer(), VALID));
    }

    @Test
    void the_request_reaches_the_use_case_as_the_caller_s_command() {
        bus.answer(CashInCommand.class, command -> result("COMPLETED"));

        cashIn(tokens.customer(), VALID);

        CashInCommand command = bus.lastDispatched(CashInCommand.class);
        assertThat(command.customerId()).isEqualTo(new Id(ContractTokens.CUSTOMER_ID));
        assertThat(command.correlationId()).isEqualTo(new Id(CORRELATION_ID));
        assertThat(command.amount()).isEqualTo(new Amount(new BigDecimal("5000"), "XAF"));
        assertThat(command.paymentMethod()).isEqualTo(PaymentMethod.ORANGE_MONEY);
    }

    @Test
    void an_amount_with_a_minor_unit_is_a_400_naming_the_field() {
        assertMatches("payment/cash-in/400-amount-not-whole", cashIn(tokens.customer(), """
                {"rawPin": "1234", "amount": 5000.5, "currency": "XAF", "paymentMethod": "OM"}
                """));
    }

    @Test
    void a_missing_pin_is_a_400_naming_the_field() {
        assertMatches("payment/cash-in/400-pin-missing", cashIn(tokens.customer(), """
                {"amount": 5000, "currency": "XAF", "paymentMethod": "OM"}
                """));
    }

    @Test
    void no_token_is_a_401_in_the_security_format() {
        assertMatches("payment/cash-in/401-no-token", cashIn(null, VALID));
    }

    @Test
    void a_wrong_pin_is_a_401_in_the_problem_format() {
        bus.fail(CashInCommand.class, new PinValidationException("Invalid PIN"));

        assertMatches("payment/cash-in/401-wrong-pin", cashIn(tokens.customer(), VALID));
    }

    @Test
    void an_operator_cannot_cash_in_to_a_customer_wallet() {
        assertMatches("payment/cash-in/403-admin-token", cashIn(tokens.admin(), VALID));
    }

    @Test
    void a_wallet_that_does_not_exist_is_a_404() {
        bus.fail(CashInCommand.class, new AccountNotFoundException("Account not found for customer"));

        assertMatches("payment/cash-in/404-account-not-found", cashIn(tokens.customer(), VALID));
    }

    @Test
    void a_replayed_command_is_a_409() {
        bus.fail(CashInCommand.class, new CommandReplayedException(new Id(CORRELATION_ID)));

        assertMatches("payment/cash-in/409-replayed", cashIn(tokens.customer(), VALID));
    }

    @Test
    void a_blocked_wallet_is_a_422() {
        bus.fail(CashInCommand.class, new AccountBlockedException("Account is blocked"));

        assertMatches("payment/cash-in/422-account-blocked", cashIn(tokens.customer(), VALID));
    }

    @Test
    void a_provider_refusal_is_a_502() {
        bus.fail(CashInCommand.class, new PaymentProviderException("Provider refused the credit"));

        assertMatches("payment/cash-in/502-provider-refusal", cashIn(tokens.customer(), VALID));
    }

    @Test
    void exhausted_retries_are_a_503() {
        bus.fail(CashInCommand.class, new RetryExhaustedException("Retries exhausted", new RuntimeException()));

        assertMatches("payment/cash-in/503-retries-exhausted", cashIn(tokens.customer(), VALID));
    }

    private MvcTestResult cashIn(String token, String body) {
        return post(mvc, "/payments/cash-in", token, body);
    }

    private static CashInResult result(String state) {
        return new CashInResult(
                "55555555-5555-5555-5555-555555555555",
                "TRX-20261119-B4E02D3E",
                state,
                new Amount(new BigDecimal("5000"), "XAF"));
    }
}
