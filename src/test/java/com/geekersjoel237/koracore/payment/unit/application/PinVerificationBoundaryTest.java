package com.geekersjoel237.koracore.payment.unit.application;

import com.geekersjoel237.koracore.payment.application.command.CashInCommand;
import com.geekersjoel237.koracore.payment.ports.out.security.PinVerifier;
import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;
import com.geekersjoel237.koracore.payment.ports.in.CashInCommandHandler;
import com.geekersjoel237.koracore.payment.application.usecases.CashInService;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.auth.domain.exception.PinValidationException;
import com.geekersjoel237.koracore.payment.domain.model.Ledger;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryAccountRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryAuthorizationRecordRepository;
import com.geekersjoel237.koracore.auth.unit.doubles.InMemoryCustomerRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryLedgerRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryPaymentProviderAdapter;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryTransactionRepository;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryTrxHistoricStatesRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BCrypt costs ~200 ms, so the PIN is verified outside every transaction (ADR-004): a wrong
 * PIN must never hold a pooled connection. Watches the boundary rather than the clock.
 */
class PinVerificationBoundaryTest {

    private static final Id CUSTOMER = new Id("customer-1");

    @Test
    void a_rejected_pin_stops_the_use_case_before_any_transaction_opens() {
        var boundary = new CountingBoundary();
        var provider = new InMemoryPaymentProviderAdapter(InMemoryPaymentProviderAdapter.Behavior.SUCCESS);
        PinVerifier rejecting = (customerId, pin) -> {
            throw new PinValidationException("Invalid PIN");
        };

        CashInCommandHandler cashIn = new CashInService(
                boundary, UseCaseFixtures.IMMEDIATE_RETRY, rejecting,
                new InMemoryAccountRepository(), new InMemoryCustomerRepository(),
                new InMemoryTransactionRepository(), new InMemoryTrxHistoricStatesRepository(),
                provider, new InMemoryLedgerRepository(Ledger.create(Id.generate())),
                new InMemoryAuthorizationRecordRepository());

        assertThatThrownBy(() -> cashIn.execute(new CashInCommand(Id.generate(), 
                CUSTOMER, Pin.of("0000"),
                Amount.of(BigDecimal.valueOf(5000), "XAF"), PaymentMethod.ORANGE_MONEY)))
                .isInstanceOf(PinValidationException.class);

        assertThat(boundary.executions())
                .describedAs("BCrypt must never run while a database connection is held")
                .isZero();
        assertThat(provider.getLastOperationType())
                .describedAs("and no provider call may be attempted either")
                .isNull();
    }

    private static final class CountingBoundary implements TransactionBoundary {

        private int executions;

        int executions() {
            return executions;
        }

        @Override
        public <T> T execute(Supplier<T> work) {
            executions++;
            return work.get();
        }
    }
}
