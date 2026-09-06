package com.geekersjoel237.koracore.payment.unit.application;

import com.geekersjoel237.koracore.payment.application.command.CashInCommand;
import com.geekersjoel237.koracore.shared.application.transaction.ConcurrentUpdateException;
import com.geekersjoel237.koracore.payment.ports.in.CashInCommandHandler;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;
import com.geekersjoel237.koracore.payment.application.result.PaymentResult;
import com.geekersjoel237.koracore.payment.domain.SystemConstants;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.auth.domain.enums.Role;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.auth.domain.model.Customer;
import com.geekersjoel237.koracore.payment.domain.model.Ledger;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.ports.out.security.CustomerPinEncoder;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.auth.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;
import com.geekersjoel237.koracore.auth.adapters.out.security.BCryptCustomerPinEncoder;
import com.geekersjoel237.koracore.payment.unit.application.UseCaseFixtures;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryAccountRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryAuthorizationRecordRepository;
import com.geekersjoel237.koracore.auth.unit.doubles.InMemoryCustomerRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryLedgerRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryProviderAdapter;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryTransactionRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryTrxHistoricStatesRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A cash-in opens two transactions with a provider round trip between them. The
 * second can lose a concurrency race and must be replayed — but replaying it must
 * not replay the money movement that already happened at the provider.
 *
 * <p>Wrapping the whole use case in the retry loop, which is what
 * {@code PaymentService.withOptimisticRetry} does, replays everything: a fresh
 * transaction id, a second {@code authorize}, a second {@code capture}. The customer
 * is charged twice and the first transaction is left orphaned in
 * {@code INITIALIZED}.
 *
 * <p>Nothing in the suite sees that today: every existing test runs with a boundary
 * that never fails, so the loop never turns. This one makes it turn.
 */
class RetryScopeTest {

    private static final Id CUSTOMER = new Id("customer-retry");
    private static final Pin PIN = Pin.of("123456");

    private final CustomerPinEncoder pinEncoder = new BCryptCustomerPinEncoder();

    @Test
    void a_lost_race_on_the_second_transaction_does_not_charge_the_customer_twice() {
        var accounts = new InMemoryAccountRepository();
        var customers = new InMemoryCustomerRepository();
        var provider = new InMemoryProviderAdapter(InMemoryProviderAdapter.Behavior.SUCCESS);

        User user = User.create(CUSTOMER, "Retry Holder", "retry@example.com", Role.CUSTOMER);
        customers.save(Customer.create(user, PhoneNumber.of("+237", "600000009"), PIN, pinEncoder));
        accounts.save(Account.createCustomerAccount(Id.generate(), CUSTOMER));
        accounts.save(Account.createFloatAccount(Id.generate(), SystemConstants.PROVIDER_ID));

        // Fails the second transaction it is asked to open — TX-2 — exactly once.
        var boundary = new FailingOnceBoundary(2);

        CashInCommandHandler cashIn = UseCaseFixtures.cashIn(
                boundary, accounts, customers, provider,
                new InMemoryTransactionRepository(), new InMemoryTrxHistoricStatesRepository(),
                new InMemoryLedgerRepository(Ledger.create(Id.generate())),
                new InMemoryAuthorizationRecordRepository(), pinEncoder);

        PaymentResult result = cashIn.execute(new CashInCommand(Id.generate(), 
                CUSTOMER, PIN, Amount.of(BigDecimal.valueOf(10000), "XAF"),
                PaymentMethod.ORANGE_MONEY));

        assertThat(result.state())
                .describedAs("the retry must still produce a completed payment")
                .isEqualTo("COMPLETED");
        assertThat(provider.captureCalls())
                .describedAs("replaying TX-2 must not replay the capture — that is a double charge")
                .isEqualTo(1);
        assertThat(provider.authorizeCalls())
                .describedAs("nor the authorization")
                .isEqualTo(1);
    }

    /** Throws on one chosen transaction, then behaves. */
    private static final class FailingOnceBoundary implements TransactionBoundary {

        private final int failOnExecution;
        private int executions;
        private boolean alreadyFailed;

        private FailingOnceBoundary(int failOnExecution) {
            this.failOnExecution = failOnExecution;
        }

        @Override
        public <T> T execute(Supplier<T> work) {
            executions++;
            if (executions == failOnExecution && !alreadyFailed) {
                alreadyFailed = true;
                throw new ConcurrentUpdateException(new IllegalStateException("simulated race"));
            }
            return work.get();
        }
    }
}
