package com.geekersjoel237.koracore.payment.unit.application;

import com.geekersjoel237.koracore.auth.adapters.out.security.BCryptCustomerPinEncoder;
import com.geekersjoel237.koracore.auth.adapters.out.security.CustomerPinVerifier;
import com.geekersjoel237.koracore.auth.domain.enums.Role;
import com.geekersjoel237.koracore.auth.domain.model.Customer;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.ports.out.security.CustomerPinEncoder;
import com.geekersjoel237.koracore.auth.unit.doubles.InMemoryCustomerRepository;
import com.geekersjoel237.koracore.payment.application.command.cashIn.CashInCommand;
import com.geekersjoel237.koracore.payment.application.command.cashIn.CashInService;
import com.geekersjoel237.koracore.payment.domain.SystemConstants;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.domain.model.Ledger;
import com.geekersjoel237.koracore.payment.ports.in.CashInCommandHandler;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryAccountRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryAuthorizationRecordRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryLedgerRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryPaymentProviderAdapter;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryTransactionRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryTrxHistoricStatesRepository;
import com.geekersjoel237.koracore.shared.application.transaction.ConcurrentUpdateException;
import com.geekersjoel237.koracore.shared.application.transaction.RetryExhaustedException;
import com.geekersjoel237.koracore.shared.application.transaction.RetryPolicy;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Only TX-2 is replayed on a lost race. Retrying the whole use case would authorize and
 * capture at the provider twice.
 */
class RetryScopeTest {

    private static final Id CUSTOMER = new Id("customer-retry");
    private static final Pin PIN = Pin.of("123456");
    private static final int TX_2 = 2;

    private final CustomerPinEncoder pinEncoder = new BCryptCustomerPinEncoder();

    private InMemoryAccountRepository accounts;
    private InMemoryCustomerRepository customers;
    private InMemoryPaymentProviderAdapter provider;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccountRepository();
        customers = new InMemoryCustomerRepository();
        provider = new InMemoryPaymentProviderAdapter(InMemoryPaymentProviderAdapter.Behavior.SUCCESS);

        User user = User.create(CUSTOMER, "Retry Holder", "retry@example.com", Role.CUSTOMER);
        customers.save(Customer.create(user, PhoneNumber.of("+237", "600000009"), PIN, pinEncoder));
        accounts.save(Account.createCustomerAccount(Id.generate(), CUSTOMER));
        accounts.save(Account.createFloatAccount(Id.generate(), SystemConstants.PROVIDER_ID));
    }

    @AfterEach
    void clearTheInterruptFlag() {
        Thread.interrupted();
    }

    private CashInCommandHandler cashIn(TransactionBoundary boundary, RetryPolicy retry) {
        var historics = new InMemoryTrxHistoricStatesRepository();
        return new CashInService(boundary, retry, new CustomerPinVerifier(customers, pinEncoder), accounts, customers,
                new InMemoryTransactionRepository(historics), historics, provider,
                new InMemoryLedgerRepository(Ledger.create(Id.generate())),
                new InMemoryAuthorizationRecordRepository());
    }

    private CashInCommand deposit() {
        return new CashInCommand(Id.generate(), CUSTOMER, PIN,
                Amount.of(BigDecimal.valueOf(10000), "XAF"), PaymentMethod.ORANGE_MONEY);
    }

    @Test
    void a_lost_race_on_the_second_transaction_does_not_charge_the_customer_twice() {
        var boundary = new LosingRacesBoundary(TX_2, 1);

        var result = cashIn(boundary, UseCaseFixtures.IMMEDIATE_RETRY).execute(deposit());

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

    @Test
    void a_race_lost_on_every_attempt_gives_up_after_waiting_between_the_attempts() {
        var boundary = new LosingRacesBoundary(TX_2, Integer.MAX_VALUE);

        assertThatThrownBy(() -> cashIn(boundary, RetryPolicy.defaults()).execute(deposit()))
                .isInstanceOf(RetryExhaustedException.class)
                .hasCauseInstanceOf(ConcurrentUpdateException.class);
        assertThat(boundary.lostRaces()).isEqualTo(5);
        assertThat(provider.captureCalls()).isEqualTo(1);
    }

    @Test
    void a_retry_interrupted_while_waiting_gives_up_and_keeps_the_interruption() {
        var boundary = new LosingRacesBoundary(TX_2, Integer.MAX_VALUE).interruptingOnLoss();

        assertThatThrownBy(() -> cashIn(boundary, RetryPolicy.defaults()).execute(deposit()))
                .isInstanceOf(RetryExhaustedException.class)
                .hasMessageContaining("Interrupted")
                .hasCauseInstanceOf(InterruptedException.class);
        assertThat(boundary.lostRaces()).isEqualTo(1);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void a_retry_policy_makes_at_least_one_attempt() {
        assertThatThrownBy(() -> new RetryPolicy(0, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class LosingRacesBoundary implements TransactionBoundary {

        private final int losingExecution;
        private final int racesToLose;
        private boolean interruptOnLoss;
        private int executions;
        private int lostRaces;

        private LosingRacesBoundary(int losingExecution, int racesToLose) {
            this.losingExecution = losingExecution;
            this.racesToLose = racesToLose;
        }

        private LosingRacesBoundary interruptingOnLoss() {
            this.interruptOnLoss = true;
            return this;
        }

        private int lostRaces() {
            return lostRaces;
        }

        @Override
        public <T> T execute(Supplier<T> work) {
            executions++;
            if (executions >= losingExecution && lostRaces < racesToLose) {
                lostRaces++;
                if (interruptOnLoss) Thread.currentThread().interrupt();
                throw new ConcurrentUpdateException(new IllegalStateException("simulated race"));
            }
            return work.get();
        }
    }
}
