package com.geekersjoel237.koracore.payment.e2e;

import com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashIn.CashInRequest;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashOut.CashOutRequest;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.shared.TransactionResponse;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.transfer.TransferRequest;
import com.geekersjoel237.koracore.shared.e2e.AbstractE2ETest;
import com.geekersjoel237.koracore.shared.e2e.TestMailConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "kora.provider.behavior=SUCCESS",
                "kora.provider.simulate-latency=true",
                "kora.provider.latency.authorize-ms=500",
                "kora.provider.latency.capture-ms=500"
        })
@ActiveProfiles("test")
@Import(TestMailConfig.class)
class ConcurrentDebitE2ETest extends AbstractE2ETest {

    private static final String PIN = "1234";
    private static final BigDecimal BALANCE = new BigDecimal("10000");

    @Autowired
    private JdbcTemplate jdbc;

    private final ExecutorService pool = Executors.newFixedThreadPool(6);

    @AfterEach
    void stopThreads() {
        pool.shutdownNow();
    }

    private SetupData fundedCustomer(String email, String phone) {
        SetupData ctx = setupCustomerWithAccount(email, "Racer", "+225", phone, PIN);
        var deposit = postWithToken("/payments/cash-in",
                new CashInRequest(PIN, BALANCE, "XAF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), TransactionResponse.class);
        assertThat(deposit.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ctx;
    }

    private int cashOutAll(SetupData ctx) {
        return postWithToken("/payments/cash-out", new CashOutRequest(PIN, BALANCE, "XAF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), String.class).getStatusCode().value();
    }

    private CompletableFuture<Integer> inBackground(Supplier<Integer> request) {
        return CompletableFuture.supplyAsync(request, pool);
    }

    private CompletableFuture<Integer> onSignal(CountDownLatch start, Supplier<Integer> request) {
        return inBackground(() -> {
            try {
                start.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return request.get();
        });
    }

    private List<String> cashOutStates() {
        return jdbc.queryForList("SELECT state FROM transactions WHERE type = 'CASH_OUT'", String.class);
    }

    @Test
    void two_concurrent_cash_outs_of_the_whole_balance_pay_out_only_once() throws Exception {
        SetupData ctx = fundedCustomer("race-co@example.com", "07000008001");
        CountDownLatch start = new CountDownLatch(1);

        CompletableFuture<Integer> first = onSignal(start, () -> cashOutAll(ctx));
        CompletableFuture<Integer> second = onSignal(start, () -> cashOutAll(ctx));
        start.countDown();
        List<Integer> statuses = List.of(first.get(), second.get());

        assertThat(cashOutStates())
                .describedAs("the refused cash-out must be stopped before the provider pays, "
                        + "leaving no transaction behind")
                .containsExactly("COMPLETED");
        assertThat(statuses).containsExactlyInAnyOrder(200, 422);
    }

    @Test
    void a_transfer_cannot_spend_what_an_in_flight_cash_out_is_paying_out() throws Exception {
        SetupData payer = fundedCustomer("race-payer@example.com", "07000008002");
        setupCustomerWithAccount("race-payee@example.com", "Payee", "+225", "07000008003", PIN);

        CompletableFuture<Integer> cashOut = inBackground(() -> cashOutAll(payer));
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(10))
                .until(() -> cashOutStates().contains("INITIALIZED"));
        int transfer = postWithToken("/payments/transfer",
                new TransferRequest(PIN, BALANCE, "XAF", "+225", "07000008003"),
                payer.tokens().accessToken(), String.class).getStatusCode().value();
        int cashOutStatus = cashOut.get();

        assertThat(cashOutStates())
                .describedAs("a cash-out the provider paid must reach the ledger")
                .containsExactly("COMPLETED");
        assertThat(cashOutStatus)
                .describedAs("the cash-out started first and holds the funds")
                .isEqualTo(200);
        assertThat(transfer)
                .describedAs("the transfer must see the funds already taken")
                .isEqualTo(422);
    }

    @Test
    void six_concurrent_partial_cash_outs_never_spend_more_than_the_balance() throws Exception {
        SetupData ctx = fundedCustomer("race-many@example.com", "07000008004");
        BigDecimal part = new BigDecimal("3000");
        CountDownLatch start = new CountDownLatch(1);

        List<CompletableFuture<Integer>> requests = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++)
            requests.add(onSignal(start, () -> postWithToken("/payments/cash-out",
                    new CashOutRequest(PIN, part, "XAF", "ORANGE_MONEY"),
                    ctx.tokens().accessToken(), String.class).getStatusCode().value()));
        start.countDown();
        List<Integer> statuses = new java.util.ArrayList<>();
        for (CompletableFuture<Integer> request : requests)
            statuses.add(request.get());

        assertThat(cashOutStates()).containsExactly("COMPLETED", "COMPLETED", "COMPLETED");
        assertThat(statuses).containsExactlyInAnyOrder(200, 200, 200, 422, 422, 422);
        assertThat(jdbc.queryForObject("SELECT balance_amount FROM accounts WHERE id = ?",
                BigDecimal.class, ctx.accountId().value())).isEqualByComparingTo("1000");
        assertThat(jdbc.queryForList("SELECT status FROM funds_holds", String.class))
                .containsExactly("CONSUMED", "CONSUMED", "CONSUMED");
        assertThat(jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN type = 'DEBIT' THEN amount ELSE -amount END), 0)
                FROM ledger_entries
                """, BigDecimal.class)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM ledger_entries
                WHERE account_id = ? AND type = 'DEBIT'
                """, BigDecimal.class, ctx.accountId().value())).isEqualByComparingTo("9000");
    }
}
