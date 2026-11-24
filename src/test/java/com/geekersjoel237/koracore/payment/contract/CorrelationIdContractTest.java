package com.geekersjoel237.koracore.payment.contract;

import com.geekersjoel237.koracore.payment.adapters.in.rest.api.balance.BalanceAction;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashIn.CashInAction;
import com.geekersjoel237.koracore.payment.application.command.cashIn.CashInCommand;
import com.geekersjoel237.koracore.payment.application.command.cashIn.CashInResult;
import com.geekersjoel237.koracore.payment.application.query.balance.BalanceResult;
import com.geekersjoel237.koracore.payment.domain.exception.AccountBlockedException;
import com.geekersjoel237.koracore.shared.contract.ContractTest;
import com.geekersjoel237.koracore.shared.contract.ContractTokens;
import com.geekersjoel237.koracore.shared.contract.ProgrammableCommandBus;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@ContractTest({CashInAction.class, BalanceAction.class})
@Import(PaymentQueryDoubles.class)
class CorrelationIdContractTest {

    private static final String HEADER = "X-Correlation-Id";
    private static final String SENT = "44444444-4444-4444-4444-444444444444";
    private static final String VALID = """
            {"rawPin": "1234", "amount": 5000, "currency": "XAF", "paymentMethod": "OM"}
            """;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ProgrammableCommandBus bus;

    @Autowired
    private ContractTokens tokens;

    @Autowired
    private PaymentQueryDoubles.ProgrammableBalance balance;

    @BeforeEach
    void reset() {
        bus.reset();
        bus.answer(CashInCommand.class, command -> new CashInResult("55555555-5555-5555-5555-555555555555",
                "TRX-20261124-B4E02D3E", "COMPLETED", Amount.of(new BigDecimal("5000"), "XAF")));
    }

    private MvcTestResult cashIn(String token, String correlationId) {
        var request = mvc.post().uri("/payments/cash-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID);
        if (token != null) request = request.header("Authorization", "Bearer " + token);
        if (correlationId != null) request = request.header(HEADER, correlationId);
        return request.exchange();
    }

    private static String problemCorrelationId(MvcTestResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return JsonPath.read(body, "$.correlationId");
    }

    @Test
    void a_command_answers_with_the_correlation_id_it_was_sent() {
        MvcTestResult result = cashIn(tokens.customer(), SENT);

        assertThat(result.getResponse().getHeader(HEADER)).isEqualTo(SENT);
        assertThat(bus.lastDispatched(CashInCommand.class).correlationId()).isEqualTo(new Id(SENT));
    }

    @Test
    void a_command_sent_without_one_answers_with_the_one_it_was_given() {
        MvcTestResult result = cashIn(tokens.customer(), null);

        String generated = result.getResponse().getHeader(HEADER);
        assertThat(generated).isNotBlank();
        assertThat(bus.lastDispatched(CashInCommand.class).correlationId()).isEqualTo(new Id(generated));
    }

    @Test
    void a_refused_command_carries_its_correlation_id_in_the_header_and_in_the_problem() throws Exception {
        bus.fail(CashInCommand.class, new AccountBlockedException("Account is blocked"));

        MvcTestResult result = cashIn(tokens.customer(), null);

        String generated = result.getResponse().getHeader(HEADER);
        assertThat(generated).isNotBlank();
        assertThat(problemCorrelationId(result)).isEqualTo(generated);
    }

    @Test
    void a_command_refused_by_security_still_carries_its_correlation_id() throws Exception {
        MvcTestResult result = cashIn(null, SENT);

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getHeader(HEADER)).isEqualTo(SENT);
        assertThat(problemCorrelationId(result)).isEqualTo(SENT);
    }

    @Test
    void a_read_gets_no_correlation_id_it_was_not_sent() {
        balance.answer(query -> new BalanceResult("77777777-7777-7777-7777-777777777777",
                "ACC-20261124-D6A24F5A", Amount.of(new BigDecimal("5000"), "XAF"),
                Amount.of(new BigDecimal("5000"), "XAF")));

        MvcTestResult result = mvc.get().uri("/payments/balance")
                .header("Authorization", "Bearer " + tokens.customer())
                .exchange();

        assertThat(result.getResponse().getHeader(HEADER)).isNull();
    }
}
