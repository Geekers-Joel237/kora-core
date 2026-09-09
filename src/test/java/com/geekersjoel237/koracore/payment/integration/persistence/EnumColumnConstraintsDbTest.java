package com.geekersjoel237.koracore.payment.integration.persistence;

import com.geekersjoel237.koracore.payment.domain.enums.LedgerEntryType;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.ResourceType;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.payment.domain.enums.TriggerSource;
import com.geekersjoel237.koracore.payment.domain.model.AuthorizationRecord.AuthorizationStatus;
import com.geekersjoel237.koracore.payment.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.shared.integration.persistence.AbstractRepositoryTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.geekersjoel237.koracore.shared.integration.persistence.EnumColumnConstraint.allowedValues;
import static com.geekersjoel237.koracore.shared.integration.persistence.EnumColumnConstraint.namesOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins every enumerated column of the payment schema to the set the domain defines.
 *
 * <p>Before {@code V202609071725__constrain_enum_columns.sql} these columns were free
 * {@code VARCHAR(255)}. Nothing wrote a bad value — the application only ever goes
 * through {@code @Enumerated(STRING)} — but nothing stopped one either, and a
 * transaction sitting in an unknown state is a row the ledger has already counted and
 * the state machine can no longer read.
 *
 * <p>Each assertion below is an <em>equality</em>, not a containment. A constant added
 * to a Java enum without the migration that widens the constraint fails here, and so
 * does a constraint that still lists a constant the domain has dropped. That is the
 * only way the two lists stay one list.
 */
class EnumColumnConstraintsDbTest extends AbstractRepositoryTest {

    @PersistenceContext
    private EntityManager em;

    // ── accounts ──────────────────────────────────────────────────────────────

    @Test
    void accounts_resource_type_accepts_exactly_the_resource_types() {
        assertThat(allowedValues(em, "accounts", "ck_accounts_resource_type"))
                .isEqualTo(namesOf(ResourceType.values()));
    }

    // ── transactions ──────────────────────────────────────────────────────────

    /**
     * {@code TransactionState} is an interface with 11 singletons rather than an enum,
     * so the expected set is read off its declared fields. A twelfth state added to the
     * interface lands here before it lands in the database.
     */
    @Test
    void transactions_state_accepts_exactly_the_lifecycle_states() {
        assertThat(allowedValues(em, "transactions", "ck_transactions_state"))
                .isEqualTo(lifecycleStates());
    }

    @Test
    void transactions_type_accepts_exactly_the_transaction_types() {
        assertThat(allowedValues(em, "transactions", "ck_transactions_type"))
                .isEqualTo(namesOf(TransactionType.values()));
    }

    /**
     * Names, not the short provider codes. {@code PaymentMethod.value()} answers
     * {@code OM} and {@code MOMO} for the web layer; the column only ever sees
     * {@code name()}, and the constraint must agree with the column.
     */
    @Test
    void transactions_payment_method_accepts_exactly_the_payment_methods() {
        assertThat(allowedValues(em, "transactions", "ck_transactions_payment_method"))
                .isEqualTo(namesOf(PaymentMethod.values()));
    }

    // ── ledger entries ────────────────────────────────────────────────────────

    @Test
    void ledger_entries_type_accepts_exactly_debit_and_credit() {
        assertThat(allowedValues(em, "ledger_entries", "ck_ledger_entries_type"))
                .isEqualTo(namesOf(LedgerEntryType.values()));
    }

    // ── state history ─────────────────────────────────────────────────────────

    @Test
    void trx_state_historics_old_state_accepts_exactly_the_lifecycle_states() {
        assertThat(allowedValues(em, "trx_state_historics", "ck_trx_state_historics_old_state"))
                .isEqualTo(lifecycleStates());
    }

    @Test
    void trx_state_historics_new_state_accepts_exactly_the_lifecycle_states() {
        assertThat(allowedValues(em, "trx_state_historics", "ck_trx_state_historics_new_state"))
                .isEqualTo(lifecycleStates());
    }

    @Test
    void trx_state_historics_triggered_by_accepts_exactly_the_trigger_sources() {
        assertThat(allowedValues(em, "trx_state_historics", "ck_trx_state_historics_triggered_by"))
                .isEqualTo(namesOf(TriggerSource.values()));
    }

    // ── authorization records ─────────────────────────────────────────────────

    @Test
    void authorization_records_status_accepts_exactly_the_authorization_statuses() {
        assertThat(allowedValues(em, "authorization_records", "ck_authorization_records_status"))
                .isEqualTo(namesOf(AuthorizationStatus.values()));
    }

    // ── the constraints are live, not merely present ──────────────────────────

    /**
     * Reading the catalog proves the constraint was written. This proves the database
     * acts on it, on the column where a wrong value is worst: an unknown state is a
     * transaction the lifecycle can neither advance nor reverse.
     */
    @Test
    void the_database_refuses_a_state_the_domain_does_not_define() {
        assertThatThrownBy(() -> insertTransactionInState("TELEPORTED"))
                .hasStackTraceContaining("violates check constraint")
                .hasStackTraceContaining("ck_transactions_state");
    }

    /**
     * A nullable enumerated column is still a closed set. {@code old_state} is NULL on a
     * transaction's first transition, which the constraint allows because
     * {@code NULL IN (...)} is UNKNOWN and a CHECK only fails on FALSE — but 'WOBBLY' is
     * FALSE, and has to be refused just the same.
     */
    @Test
    void the_database_refuses_an_unknown_old_state_even_though_the_column_is_nullable() {
        assertThatThrownBy(() -> insertHistoricWithOldState("WOBBLY"))
                .hasStackTraceContaining("violates check constraint")
                .hasStackTraceContaining("ck_trx_state_historics_old_state");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Native SQL on purpose: going through the repository would only prove that
     * Hibernate refuses to map an unknown value, which is the guarantee this ticket
     * exists because we could not rely on. The insert has to reach the database.
     */
    private void insertTransactionInState(String state) {
        String id = UUID.randomUUID().toString();
        em.createNativeQuery("""
                        INSERT INTO transactions
                            (id, transaction_number, from_account_id, to_account_id, state, type,
                             payment_method, amount, currency, occurred_at, created_at, updated_at)
                        VALUES
                            (:id, :id, 'account-from', 'account-to', :state, 'CASH_IN',
                             'ORANGE_MONEY', 1000.0000, 'XAF', now(), now(), now())
                        """)
                .setParameter("id", id)
                .setParameter("state", state)
                .executeUpdate();
    }

    private void insertHistoricWithOldState(String oldState) {
        String id = UUID.randomUUID().toString();
        em.createNativeQuery("""
                        INSERT INTO trx_state_historics
                            (id, transaction_id, old_state, new_state, occurred_at, created_at, updated_at)
                        VALUES
                            (:id, :id, :oldState, 'AUTHORIZED', now(), now(), now())
                        """)
                .setParameter("id", id)
                .setParameter("oldState", oldState)
                .executeUpdate();
    }

    /** The names of the singletons declared on {@link TransactionState}. */
    private static Set<String> lifecycleStates() {
        return Arrays.stream(TransactionState.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> TransactionState.class.isAssignableFrom(field.getType()))
                .map(EnumColumnConstraintsDbTest::stateName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String stateName(Field field) {
        try {
            return ((TransactionState) field.get(null)).name();
        } catch (IllegalAccessException e) {
            throw new AssertionError("cannot read " + field.getName() + " on TransactionState", e);
        }
    }
}
