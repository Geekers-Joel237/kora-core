package com.geekersjoel237.koracore.payment.contract;

import com.geekersjoel237.koracore.payment.adapters.in.rest.api.history.HistoryAction;
import com.geekersjoel237.koracore.payment.application.query.history.HistoryCursor;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionHistoryQuery;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionKind;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionSummary;
import com.geekersjoel237.koracore.payment.domain.enums.CustomerStatus;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;
import com.geekersjoel237.koracore.shared.contract.ContractTest;
import com.geekersjoel237.koracore.shared.contract.ContractTokens;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.geekersjoel237.koracore.shared.contract.ContractRequests.get;
import static com.geekersjoel237.koracore.shared.contract.ContractSnapshot.assertMatches;
import static org.assertj.core.api.Assertions.assertThat;

@ContractTest(HistoryAction.class)
@Import(PaymentQueryDoubles.class)
class HistoryContractTest {

    private static final TransactionSummary DEPOSIT = new TransactionSummary(
            new Id("88888888-8888-8888-8888-888888888881"), "TRX-20261118-E7B35A6B",
            TransactionKind.DEPOSIT, CustomerStatus.COMPLETED,
            new Amount(new BigDecimal("20000"), "XAF"), PaymentMethod.MOBILE_MONEY, null,
            Instant.parse("2026-11-18T09:15:00Z"));
    private static final TransactionSummary TRANSFER_SENT = new TransactionSummary(
            new Id("88888888-8888-8888-8888-888888888882"), "TRX-20261119-F8C46B7C",
            TransactionKind.TRANSFER_SENT, CustomerStatus.PENDING,
            new Amount(new BigDecimal("5000"), "XAF"), null, "+237690000001",
            Instant.parse("2026-11-19T14:30:00Z"));

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private PaymentQueryDoubles.ProgrammableHistory history;

    @Autowired
    private ContractTokens tokens;

    @BeforeEach
    void reset() {
        history.reset();
    }

    @Test
    void a_page_with_more_to_come_carries_a_next_cursor() {
        history.answer(query -> new PageResult<>(List.of(TRANSFER_SENT, DEPOSIT), true));

        assertMatches("payment/history/200-page-with-next", history("/payments/history?limit=2"));
    }

    @Test
    void the_last_page_carries_no_cursor() {
        history.answer(query -> new PageResult<>(List.of(DEPOSIT), false));

        assertMatches("payment/history/200-last-page", history("/payments/history"));
    }

    @Test
    void an_empty_history_is_an_empty_page() {
        history.answer(query -> new PageResult<>(List.of(), false));

        assertMatches("payment/history/200-empty", history("/payments/history"));
    }

    @Test
    void filters_cursor_and_limit_reach_the_query_as_typed_values() {
        history.answer(query -> new PageResult<>(List.of(DEPOSIT), true));
        String cursor = new String(history("/payments/history").getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)
                .replaceAll("(?s).*\"nextCursor\":\"([^\"]+)\".*", "$1");

        history("/payments/history?status=COMPLETED&kind=DEPOSIT&from=2026-11-01&to=2026-11-19&limit=5&cursor=" + cursor);

        TransactionHistoryQuery query = history.lastQuery();
        assertThat(query.customerId()).isEqualTo(new Id(ContractTokens.CUSTOMER_ID));
        assertThat(query.filter().status()).isEqualTo(CustomerStatus.COMPLETED);
        assertThat(query.filter().kind()).isEqualTo(TransactionKind.DEPOSIT);
        assertThat(query.filter().from()).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(query.filter().to()).isEqualTo(LocalDate.parse("2026-11-19"));
        assertThat(query.pagination().limit()).isEqualTo(5);
        assertThat(query.after()).isEqualTo(new HistoryCursor(DEPOSIT.date(), DEPOSIT.transactionId()));
    }

    @Test
    void an_unknown_status_is_a_400_listing_the_accepted_values() {
        assertMatches("payment/history/400-unknown-status", history("/payments/history?status=PAID"));
    }

    @Test
    void a_date_that_is_not_iso_is_a_400() {
        assertMatches("payment/history/400-date-not-iso", history("/payments/history?from=19-11-2026"));
    }

    @Test
    void a_from_after_to_is_a_400() {
        assertMatches("payment/history/400-from-after-to",
                history("/payments/history?from=2026-11-19&to=2026-11-01"));
    }

    @Test
    void a_limit_above_the_maximum_is_a_400() {
        assertMatches("payment/history/400-limit-above-maximum", history("/payments/history?limit=101"));
    }

    @Test
    void an_unreadable_cursor_is_a_400() {
        assertMatches("payment/history/400-cursor-invalid", history("/payments/history?cursor=not-a-cursor"));
    }

    @Test
    void a_wallet_that_does_not_exist_is_a_404() {
        history.fail(new AccountNotFoundException("Account not found for customer"));

        assertMatches("payment/history/404-account-not-found", history("/payments/history"));
    }

    @Test
    void no_token_is_a_401_unauthenticated() {
        assertMatches("payment/history/401-no-token", get(mvc, "/payments/history", null));
    }

    @Test
    void an_operator_cannot_read_a_customer_history() {
        assertMatches("payment/history/403-admin-token", get(mvc, "/payments/history", tokens.admin()));
    }

    private MvcTestResult history(String pathAndQuery) {
        return get(mvc, pathAndQuery, tokens.customer());
    }
}
