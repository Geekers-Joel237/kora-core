package com.geekersjoel237.koracore.payment.contract;

import com.geekersjoel237.koracore.auth.domain.exception.PinValidationException;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashOut.CashOutAction;
import com.geekersjoel237.koracore.payment.application.command.cashOut.CashOutCommand;
import com.geekersjoel237.koracore.payment.application.command.cashOut.CashOutResult;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.payment.domain.exception.InsufficientFundsException;
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

import static com.geekersjoel237.koracore.shared.contract.ContractSnapshot.assertMatches;
import static com.geekersjoel237.koracore.shared.contract.ContractRequests.CORRELATION_ID;
import static com.geekersjoel237.koracore.shared.contract.ContractRequests.post;
import static org.assertj.core.api.Assertions.assertThat;

@ContractTest(CashOutAction.class)
class CashOutContractTest {

    private static final String VALID = """
            {"rawPin": "1234", "amount": 5000, "currency": "XAF", "paymentMethod": "MOMO"}
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
    void a_completed_cash_out_answers_200_with_the_transaction() {
        bus.answer(CashOutCommand.class, command -> result("COMPLETED"));

        assertMatches("payment/cash-out/200-completed", cashOut(tokens.customer(), VALID));
    }

    @Test
    void the_request_reaches_the_use_case_as_the_caller_s_command() {
        bus.answer(CashOutCommand.class, command -> result("COMPLETED"));

        cashOut(tokens.customer(), VALID);

        CashOutCommand command = bus.lastDispatched(CashOutCommand.class);
        assertThat(command.customerId()).isEqualTo(new Id(ContractTokens.CUSTOMER_ID));
        assertThat(command.correlationId()).isEqualTo(new Id(CORRELATION_ID));
        assertThat(command.amount()).isEqualTo(new Amount(new BigDecimal("5000"), "XAF"));
        assertThat(command.paymentMethod()).isEqualTo(PaymentMethod.MOBILE_MONEY);
    }

    @Test
    void an_unknown_provider_outcome_answers_200_in_state_unknown() {
        bus.answer(CashOutCommand.class, command -> result("UNKNOWN"));

        assertMatches("payment/cash-out/200-unknown", cashOut(tokens.customer(), VALID));
    }

    @Test
    void an_amount_below_one_is_a_400_naming_the_field() {
        assertMatches("payment/cash-out/400-amount-below-minimum", cashOut(tokens.customer(), """
                {"rawPin": "1234", "amount": 0, "currency": "XAF", "paymentMethod": "MOMO"}
                """));
    }

    @Test
    void a_currency_that_is_not_three_letters_is_a_400_naming_the_field() {
        assertMatches("payment/cash-out/400-currency-not-iso", cashOut(tokens.customer(), """
                {"rawPin": "1234", "amount": 5000, "currency": "XA", "paymentMethod": "MOMO"}
                """));
    }

    @Test
    void an_unknown_payment_method_is_a_400() {
        assertMatches("payment/cash-out/400-unknown-payment-method", cashOut(tokens.customer(), """
                {"rawPin": "1234", "amount": 5000, "currency": "XAF", "paymentMethod": "BITCOIN"}
                """));
    }

    @Test
    void a_body_that_is_not_json_is_a_400() {
        assertMatches("payment/cash-out/400-malformed-body", cashOut(tokens.customer(), "{\"amount\": "));
    }

    @Test
    void no_token_is_a_401_unauthenticated() {
        assertMatches("payment/cash-out/401-no-token", cashOut(null, VALID));
    }

    @Test
    void an_unreadable_token_is_a_401_unauthenticated() {
        assertMatches("payment/cash-out/401-invalid-token", cashOut("not-a-token", VALID));
    }

    @Test
    void a_refresh_token_is_not_accepted_as_an_access_token() {
        assertMatches("payment/cash-out/401-refresh-token", cashOut(tokens.customerRefreshToken(), VALID));
    }

    @Test
    void a_wrong_pin_is_a_401_invalid_pin_not_unauthenticated() {
        bus.fail(CashOutCommand.class, new PinValidationException("Invalid PIN"));

        assertMatches("payment/cash-out/401-wrong-pin", cashOut(tokens.customer(), VALID));
    }

    @Test
    void an_operator_cannot_cash_out_a_customer_wallet() {
        assertMatches("payment/cash-out/403-admin-token", cashOut(tokens.admin(), VALID));
    }

    @Test
    void a_wallet_that_does_not_exist_is_a_404() {
        bus.fail(CashOutCommand.class, new AccountNotFoundException("Account not found for customer"));

        assertMatches("payment/cash-out/404-account-not-found", cashOut(tokens.customer(), VALID));
    }

    @Test
    void a_replayed_command_is_a_409() {
        bus.fail(CashOutCommand.class, new CommandReplayedException(new Id(CORRELATION_ID)));

        assertMatches("payment/cash-out/409-replayed", cashOut(tokens.customer(), VALID));
    }

    @Test
    void insufficient_funds_is_a_422() {
        bus.fail(CashOutCommand.class, new InsufficientFundsException("Insufficient funds"));

        assertMatches("payment/cash-out/422-insufficient-funds", cashOut(tokens.customer(), VALID));
    }

    @Test
    void a_provider_refusal_is_a_502() {
        bus.fail(CashOutCommand.class, new PaymentProviderException("Provider refused the debit"));

        assertMatches("payment/cash-out/502-provider-refusal", cashOut(tokens.customer(), VALID));
    }

    @Test
    void exhausted_retries_are_a_503() {
        bus.fail(CashOutCommand.class, new RetryExhaustedException("Retries exhausted", new RuntimeException()));

        assertMatches("payment/cash-out/503-retries-exhausted", cashOut(tokens.customer(), VALID));
    }

    private MvcTestResult cashOut(String token, String body) {
        return post(mvc, "/payments/cash-out", token, body);
    }

    private static CashOutResult result(String state) {
        return new CashOutResult(
                "44444444-4444-4444-4444-444444444444",
                "TRX-20261119-A3F91C2D",
                state,
                new Amount(new BigDecimal("5000"), "XAF"));
    }
}
