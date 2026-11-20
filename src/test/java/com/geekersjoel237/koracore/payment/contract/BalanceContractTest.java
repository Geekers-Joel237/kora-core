package com.geekersjoel237.koracore.payment.contract;

import com.geekersjoel237.koracore.payment.adapters.in.rest.api.balance.BalanceAction;
import com.geekersjoel237.koracore.payment.application.query.balance.BalanceResult;
import com.geekersjoel237.koracore.payment.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.shared.contract.ContractTest;
import com.geekersjoel237.koracore.shared.contract.ContractTokens;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;

import static com.geekersjoel237.koracore.shared.contract.ContractRequests.get;
import static com.geekersjoel237.koracore.shared.contract.ContractSnapshot.assertMatches;
import static org.assertj.core.api.Assertions.assertThat;

@ContractTest(BalanceAction.class)
@Import(PaymentQueryDoubles.class)
class BalanceContractTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private PaymentQueryDoubles.ProgrammableBalance balance;

    @Autowired
    private ContractTokens tokens;

    @BeforeEach
    void reset() {
        balance.reset();
    }

    @Test
    void the_balance_answers_200_with_available_and_ledger_amounts() {
        balance.answer(query -> new BalanceResult(
                "77777777-7777-7777-7777-777777777777",
                "ACC-20261119-D6A24F5A",
                new Amount(new BigDecimal("15000"), "XAF"),
                new Amount(new BigDecimal("10000"), "XAF")));

        assertMatches("payment/balance/200-with-a-hold", get(mvc, "/payments/balance", tokens.customer()));
    }

    @Test
    void the_balance_asked_for_is_the_caller_s_own() {
        balance.answer(query -> new BalanceResult(
                "77777777-7777-7777-7777-777777777777",
                "ACC-20261119-D6A24F5A",
                new Amount(BigDecimal.ZERO, "XAF"),
                new Amount(BigDecimal.ZERO, "XAF")));

        get(mvc, "/payments/balance", tokens.customer());

        assertThat(balance.lastQuery().customerId()).isEqualTo(new Id(ContractTokens.CUSTOMER_ID));
    }

    @Test
    void no_token_is_a_401_in_the_security_format() {
        assertMatches("payment/balance/401-no-token", get(mvc, "/payments/balance", null));
    }

    @Test
    void an_operator_cannot_read_a_customer_balance() {
        assertMatches("payment/balance/403-admin-token", get(mvc, "/payments/balance", tokens.admin()));
    }

    @Test
    void a_wallet_that_does_not_exist_is_a_404() {
        balance.fail(new AccountNotFoundException("Account not found for customer"));

        assertMatches("payment/balance/404-account-not-found", get(mvc, "/payments/balance", tokens.customer()));
    }
}
