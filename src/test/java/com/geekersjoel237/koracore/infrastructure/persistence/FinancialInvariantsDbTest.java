package com.geekersjoel237.koracore.infrastructure.persistence;

import com.geekersjoel237.koracore.application.command.CashInCommand;
import com.geekersjoel237.koracore.application.command.CashOutCommand;
import com.geekersjoel237.koracore.application.port.in.PaymentService;
import com.geekersjoel237.koracore.domain.enums.Role;
import com.geekersjoel237.koracore.domain.model.Account;
import com.geekersjoel237.koracore.domain.model.Customer;
import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.model.User;
import com.geekersjoel237.koracore.domain.port.AccountRepository;
import com.geekersjoel237.koracore.domain.port.CustomerPinEncoder;
import com.geekersjoel237.koracore.domain.port.CustomerRepository;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.domain.vo.PhoneNumber;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL-level invariant tests that verify fintech correctness properties
 * directly in the database after each operation.
 *
 * Uses EntityManager.createNativeQuery() so em.flush() is required before
 * each assertion (native SQL bypasses Hibernate's FlushMode.AUTO).
 */
class FinancialInvariantsDbTest extends AbstractRepositoryTest {

    private static final Id     SYSTEM_PROVIDER_ID = new Id("provider-system-001");
    private static final Amount CASH_IN_AMOUNT     = new Amount(new BigDecimal("10000.00"), "XOF");
    private static final Amount CASH_OUT_AMOUNT    = new Amount(new BigDecimal("3000.00"),  "XOF");

    @Autowired private PaymentService    paymentService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AccountRepository  accountRepository;
    @Autowired private CustomerPinEncoder pinEncoder;

    @PersistenceContext
    private EntityManager em;

    // ── setup helper ──────────────────────────────────────────────────────────

    private SetupResult setup(String email, String phoneNumber) {
        Id customerId = Id.generate();
        User user = User.create(customerId, "Test User", email, Role.CUSTOMER);
        Customer customer = Customer.create(user, PhoneNumber.of("+225", phoneNumber), "1234", pinEncoder);
        customerRepository.save(customer);

        Id customerAccountId = Id.generate();
        accountRepository.save(Account.createCustomerAccount(customerAccountId, customerId));

        accountRepository.save(Account.createFloatAccount(Id.generate(), SYSTEM_PROVIDER_ID));

        return new SetupResult(customerId, customerAccountId);
    }

    private record SetupResult(Id customerId, Id customerAccountId) {}

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void double_entry_is_maintained_after_cash_in() {
        SetupResult ctx = setup("inv1@example.com", "07000000101");
        paymentService.cashIn(new CashInCommand(ctx.customerId(), "1234", CASH_IN_AMOUNT, "ORANGE_MONEY"));
        em.flush();

        Number imbalance = (Number) em.createNativeQuery("""
                SELECT SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE 0 END)
                     - SUM(CASE WHEN type = 'DEBIT'  THEN amount ELSE 0 END)
                FROM operations
                WHERE deleted_at IS NULL
                """)
                .getSingleResult();

        assertThat(new BigDecimal(imbalance.toString()))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void each_cash_in_transaction_has_exactly_two_operations() {
        SetupResult ctx = setup("inv2@example.com", "07000000102");
        Transaction tx = paymentService.cashIn(
                new CashInCommand(ctx.customerId(), "1234", CASH_IN_AMOUNT, "ORANGE_MONEY"));
        em.flush();

        String txId = tx.snapshot().transactionId().value();
        Number opCount = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM operations
                WHERE transaction_id = :txId AND deleted_at IS NULL
                """)
                .setParameter("txId", txId)
                .getSingleResult();

        assertThat(opCount.longValue()).isEqualTo(2L);
    }

    @Test
    void no_orphan_operations_exist_in_database() {
        SetupResult ctx = setup("inv3@example.com", "07000000103");
        paymentService.cashIn(new CashInCommand(ctx.customerId(), "1234", CASH_IN_AMOUNT, "ORANGE_MONEY"));
        em.flush();

        Number orphanCount = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM operations o
                WHERE o.deleted_at IS NULL
                  AND o.transaction_id NOT IN (
                      SELECT t.id FROM transactions t WHERE t.deleted_at IS NULL
                  )
                """)
                .getSingleResult();

        assertThat(orphanCount.longValue()).isZero();
    }

    @Test
    void double_entry_is_maintained_per_transaction_after_cash_in_and_cash_out() {
        SetupResult ctx = setup("inv4@example.com", "07000000104");
        paymentService.cashIn(
                new CashInCommand(ctx.customerId(), "1234", CASH_IN_AMOUNT, "ORANGE_MONEY"));
        paymentService.cashOut(
                new CashOutCommand(ctx.customerId(), "1234", CASH_OUT_AMOUNT, "ORANGE_MONEY"));
        em.flush();

        // Count transactions where SUM(CREDIT) != SUM(DEBIT) — must be 0
        Number violations = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM (
                    SELECT transaction_id
                    FROM operations
                    WHERE deleted_at IS NULL
                    GROUP BY transaction_id
                    HAVING SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE 0 END)
                        != SUM(CASE WHEN type = 'DEBIT'  THEN amount ELSE 0 END)
                ) v
                """)
                .getSingleResult();

        assertThat(violations.longValue()).isZero();
    }

    @Test
    void customer_account_balance_equals_net_cash_flow_from_operations() {
        SetupResult ctx = setup("inv5@example.com", "07000000105");
        paymentService.cashIn(
                new CashInCommand(ctx.customerId(), "1234", CASH_IN_AMOUNT, "ORANGE_MONEY"));
        paymentService.cashOut(
                new CashOutCommand(ctx.customerId(), "1234", CASH_OUT_AMOUNT, "ORANGE_MONEY"));
        em.flush();

        String accountId = ctx.customerAccountId().value();

        BigDecimal storedBalance = new BigDecimal(
                em.createNativeQuery(
                        "SELECT balance_amount FROM accounts WHERE id = :id AND deleted_at IS NULL")
                        .setParameter("id", accountId)
                        .getSingleResult().toString());

        BigDecimal netFlow = new BigDecimal(
                em.createNativeQuery("""
                        SELECT SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE -amount END)
                        FROM operations
                        WHERE account_id = :accountId AND deleted_at IS NULL
                        """)
                        .setParameter("accountId", accountId)
                        .getSingleResult().toString());

        assertThat(storedBalance).isEqualByComparingTo(netFlow);
    }
}