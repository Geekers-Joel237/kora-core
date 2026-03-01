package com.geekersjoel237.koracore.e2e;

import com.geekersjoel237.koracore.web.api.payment.CashInRequest;
import com.geekersjoel237.koracore.web.api.payment.CashOutRequest;
import com.geekersjoel237.koracore.web.api.payment.TransactionResponse;
import com.geekersjoel237.koracore.web.api.payment.TransferRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyIntegrityE2ETest extends AbstractE2ETest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void balance_is_zero_after_cash_in_and_equal_cash_out() {
        BigDecimal amount = new BigDecimal("5000.00");
        SetupData ctx = setupCustomerWithAccount("mi1@example.com", "MI1", "+225", "07000004001", "1234");

        postWithToken("/payments/cash-in",
                new CashInRequest("1234", amount, "XOF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), TransactionResponse.class);
        postWithToken("/payments/cash-out",
                new CashOutRequest("1234", amount, "XOF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), TransactionResponse.class);

        var account = accountRepository.findByCustomerId(ctx.customerId()).orElseThrow();
        assertThat(account.snapshot().balance().solde().value())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void sum_of_all_operations_credits_minus_debits_is_zero() {
        BigDecimal amount = new BigDecimal("2000.00");
        SetupData ctx = setupCustomerWithAccount("mi2@example.com", "MI2", "+225", "07000004002", "1234");

        postWithToken("/payments/cash-in",
                new CashInRequest("1234", amount, "XOF", "ORANGE_MONEY"),
                ctx.tokens().accessToken(), TransactionResponse.class);

        BigDecimal imbalance = new BigDecimal(jdbcTemplate.queryForObject("""
                SELECT SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE 0 END)
                     - SUM(CASE WHEN type = 'DEBIT'  THEN amount ELSE 0 END)
                FROM operations WHERE deleted_at IS NULL
                """, Object.class).toString());

        assertThat(imbalance).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void transfer_keeps_total_balance_constant() {
        BigDecimal cashIn   = new BigDecimal("10000.00");
        BigDecimal transfer = new BigDecimal("4000.00");

        SetupData sender   = setupCustomerWithAccount("mi3s@example.com", "MI3S", "+225", "07000004003", "1234");
        SetupData receiver = setupCustomerWithAccount("mi3r@example.com", "MI3R", "+225", "07000004004", "5678");

        postWithToken("/payments/cash-in",
                new CashInRequest("1234", cashIn, "XOF", "ORANGE_MONEY"),
                sender.tokens().accessToken(), TransactionResponse.class);

        postWithToken("/payments/transfer",
                new TransferRequest("1234", transfer, "XOF", "ORANGE_MONEY", "+22507000004004"),
                sender.tokens().accessToken(), TransactionResponse.class);

        var senderBalance   = accountRepository.findByCustomerId(sender.customerId()).orElseThrow()
                .snapshot().balance().solde().value();
        var receiverBalance = accountRepository.findByCustomerId(receiver.customerId()).orElseThrow()
                .snapshot().balance().solde().value();

        assertThat(senderBalance.add(receiverBalance)).isEqualByComparingTo(cashIn);
    }

    @Test
    void concurrent_cash_ins_all_return_200() throws Exception {
        int threadCount = 5;
        BigDecimal perCashIn = new BigDecimal("1000.00");

        SetupData ctx = setupCustomerWithAccount("mi4@example.com", "MI4", "+225", "07000004005", "1234");
        String token = ctx.tokens().accessToken();

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> postWithToken("/payments/cash-in",
                    new CashInRequest("1234", perCashIn, "XOF", "ORANGE_MONEY"),
                    token, TransactionResponse.class).getStatusCode().value()));
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) statuses.add(f.get());
        pool.shutdown();

        // All requests must return 200 OK (balance consistency under concurrency is a Step 1+ concern)
        assertThat(statuses).allMatch(s -> s == 200);
    }

    @Test
    void double_entry_invariant_holds_after_multiple_operations() {
        BigDecimal cashIn   = new BigDecimal("8000.00");
        BigDecimal cashOut  = new BigDecimal("3000.00");
        BigDecimal transfer = new BigDecimal("2000.00");

        SetupData sender   = setupCustomerWithAccount("mi5s@example.com", "MI5S", "+225", "07000004006", "1234");
        SetupData receiver = setupCustomerWithAccount("mi5r@example.com", "MI5R", "+225", "07000004007", "5678");

        postWithToken("/payments/cash-in",
                new CashInRequest("1234", cashIn, "XOF", "ORANGE_MONEY"),
                sender.tokens().accessToken(), TransactionResponse.class);
        postWithToken("/payments/cash-out",
                new CashOutRequest("1234", cashOut, "XOF", "ORANGE_MONEY"),
                sender.tokens().accessToken(), TransactionResponse.class);
        postWithToken("/payments/transfer",
                new TransferRequest("1234", transfer, "XOF", "ORANGE_MONEY", "+22507000004007"),
                sender.tokens().accessToken(), TransactionResponse.class);

        // For every transaction, credits must equal debits
        Long violations = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT transaction_id
                    FROM operations WHERE deleted_at IS NULL
                    GROUP BY transaction_id
                    HAVING SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE 0 END)
                        != SUM(CASE WHEN type = 'DEBIT'  THEN amount ELSE 0 END)
                ) v
                """, Long.class);

        assertThat(violations).isZero();
    }
}