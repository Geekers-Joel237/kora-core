package com.geekersjoel237.koracore.infrastructure.persistence;

import com.geekersjoel237.koracore.domain.enums.OperationType;
import com.geekersjoel237.koracore.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.domain.enums.ResourceType;
import com.geekersjoel237.koracore.domain.enums.Role;
import com.geekersjoel237.koracore.domain.enums.TransactionType;
import com.geekersjoel237.koracore.domain.enums.TriggerSource;
import com.geekersjoel237.koracore.domain.enums.UserStatus;
import com.geekersjoel237.koracore.domain.model.AuthorizationRecord.AuthorizationStatus;
import com.geekersjoel237.koracore.domain.model.state.TransactionState;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the ADR-007 CHECK constraints directly against PostgreSQL: an
 * out-of-enumeration value must be rejected by the database (not just by the
 * domain), and every real Java constant must round-trip through it.
 *
 * Inserts go through native SQL rather than the entities: the whole point is
 * to simulate a write that bypasses the domain (migration, hotfix, import) —
 * most of the touched columns are now {@code @Enumerated(STRING)}-typed on
 * their entity, so an invalid string can no longer even be constructed
 * through the Java API.
 */
class EnumConstraintsDbTest extends AbstractRepositoryTest {

    private static final List<TransactionState> ALL_STATES = List.of(
            TransactionState.INITIALIZED, TransactionState.AUTHORIZED, TransactionState.CAPTURED,
            TransactionState.SETTLEMENT_PENDING, TransactionState.SETTLED, TransactionState.COMPLETED,
            TransactionState.FAILED, TransactionState.AUTHORIZATION_FAILED, TransactionState.CAPTURE_FAILED,
            TransactionState.SETTLEMENT_FAILED, TransactionState.REVERSED
    );

    private static final String JUNK = "NOT_A_REAL_VALUE";

    @PersistenceContext
    private EntityManager em;

    // ── insert helpers — minimal NOT NULL columns per table, no FK constraints declared ──

    private void insertUser(String role, String status) {
        String id = UUID.randomUUID().toString();
        em.createNativeQuery("""
                INSERT INTO users (id, full_name, email, role, status, version, created_at, updated_at, deleted)
                VALUES (:id, 'Test User', :email, :role, :status, 0, now(), now(), false)
                """)
                .setParameter("id", id)
                .setParameter("email", id + "@example.com")
                .setParameter("role", role)
                .setParameter("status", status)
                .executeUpdate();
    }

    private void insertAccount(String resourceType) {
        String id = UUID.randomUUID().toString();
        em.createNativeQuery("""
                INSERT INTO accounts (id, account_number, resource_type, resource_id, balance_amount,
                                       balance_currency, is_blocked, version, created_at, updated_at, deleted)
                VALUES (:id, :accNum, :resourceType, :resourceId, 0, 'XOF', false, 0, now(), now(), false)
                """)
                .setParameter("id", id)
                .setParameter("accNum", "ACC-" + id)
                .setParameter("resourceType", resourceType)
                .setParameter("resourceId", id)
                .executeUpdate();
    }

    private void insertTransaction(String state, String type, String paymentMethod) {
        String id = UUID.randomUUID().toString();
        em.createNativeQuery("""
                INSERT INTO transactions (id, transaction_number, from_id, to_id, state, type, payment_method,
                                           amount, currency, occurred_at, version, created_at, updated_at, deleted)
                VALUES (:id, :txNum, 'from-dummy', 'to-dummy', :state, :type, :paymentMethod,
                        100.00, 'XOF', now(), 0, now(), now(), false)
                """)
                .setParameter("id", id)
                .setParameter("txNum", "TX-" + id)
                .setParameter("state", state)
                .setParameter("type", type)
                .setParameter("paymentMethod", paymentMethod)
                .executeUpdate();
    }

    private void insertOperation(String type) {
        String id = UUID.randomUUID().toString();
        em.createNativeQuery("""
                INSERT INTO operations (id, transaction_id, type, amount, currency, account_id,
                                         occurred_at, created_at, updated_at, deleted)
                VALUES (:id, 'tx-dummy', :type, 100.00, 'XOF', 'acc-dummy', now(), now(), now(), false)
                """)
                .setParameter("id", id)
                .setParameter("type", type)
                .executeUpdate();
    }

    private void insertTrxStateHistoric(String oldState, String newState, String triggeredBy) {
        String id = UUID.randomUUID().toString();
        em.createNativeQuery("""
                INSERT INTO trx_state_historics (id, transaction_id, old_state, new_state, triggered_by,
                                                  occurred_at, created_at, updated_at, deleted)
                VALUES (:id, 'tx-dummy', :oldState, :newState, :triggeredBy, now(), now(), now(), false)
                """)
                .setParameter("id", id)
                .setParameter("oldState", oldState)
                .setParameter("newState", newState)
                .setParameter("triggeredBy", triggeredBy)
                .executeUpdate();
    }

    private void insertAuthorizationRecord(String status) {
        String id = UUID.randomUUID().toString();
        em.createNativeQuery("""
                INSERT INTO authorization_records (id, transaction_id, provider_reference, authorized_amount,
                                                     currency, authorized_at, expires_at, status,
                                                     version, created_at, updated_at, deleted)
                VALUES (:id, 'tx-dummy', 'prov-ref', 100.00, 'XOF', now(), now() + interval '1 hour', :status,
                        0, now(), now(), false)
                """)
                .setParameter("id", id)
                .setParameter("status", status)
                .executeUpdate();
    }

    private long countRows(String table) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM " + table).getSingleResult()).longValue();
    }

    // ── rejection: an out-of-enumeration value is rejected by the database ──

    @Test
    void users_role_rejects_a_value_outside_the_enumeration() {
        assertThatThrownBy(() -> insertUser(JUNK, UserStatus.PENDING.name()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void users_status_rejects_a_value_outside_the_enumeration() {
        assertThatThrownBy(() -> insertUser(Role.CUSTOMER.name(), JUNK))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void accounts_resource_type_rejects_a_value_outside_the_enumeration() {
        assertThatThrownBy(() -> insertAccount(JUNK))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void transactions_state_rejects_a_value_outside_the_enumeration() {
        assertThatThrownBy(() -> insertTransaction(JUNK, TransactionType.CASH_IN.name(), PaymentMethod.WALLET.name()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void transactions_type_rejects_a_value_outside_the_enumeration() {
        assertThatThrownBy(() -> insertTransaction(TransactionState.INITIALIZED.name(), JUNK, PaymentMethod.WALLET.name()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void transactions_payment_method_rejects_a_value_outside_the_enumeration() {
        assertThatThrownBy(() -> insertTransaction(TransactionState.INITIALIZED.name(), TransactionType.CASH_IN.name(), JUNK))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void operations_type_rejects_a_value_outside_the_enumeration() {
        assertThatThrownBy(() -> insertOperation(JUNK))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void trx_state_historics_old_state_rejects_a_value_outside_the_enumeration() {
        assertThatThrownBy(() -> insertTrxStateHistoric(JUNK, TransactionState.INITIALIZED.name(), null))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void trx_state_historics_new_state_rejects_a_value_outside_the_enumeration() {
        assertThatThrownBy(() -> insertTrxStateHistoric(null, JUNK, null))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void trx_state_historics_triggered_by_rejects_a_value_outside_the_enumeration() {
        assertThatThrownBy(() -> insertTrxStateHistoric(null, TransactionState.INITIALIZED.name(), JUNK))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void authorization_records_status_rejects_a_value_outside_the_enumeration() {
        assertThatThrownBy(() -> insertAuthorizationRecord(JUNK))
                .isInstanceOf(PersistenceException.class);
    }

    // ── acceptance: every real Java domain constant is accepted ──

    @Test
    void every_role_constant_is_accepted() {
        long before = countRows("users");
        for (Role role : Role.values()) {
            insertUser(role.name(), UserStatus.PENDING.name());
        }
        assertThat(countRows("users")).isEqualTo(before + Role.values().length);
    }

    @Test
    void every_user_status_constant_is_accepted() {
        long before = countRows("users");
        for (UserStatus status : UserStatus.values()) {
            insertUser(Role.CUSTOMER.name(), status.name());
        }
        assertThat(countRows("users")).isEqualTo(before + UserStatus.values().length);
    }

    @Test
    void every_resource_type_constant_is_accepted() {
        // accounts already holds the float account bootstrapped by DataInitializer at startup.
        long before = countRows("accounts");
        for (ResourceType resourceType : ResourceType.values()) {
            insertAccount(resourceType.name());
        }
        assertThat(countRows("accounts")).isEqualTo(before + ResourceType.values().length);
    }

    @Test
    void every_transaction_state_constant_is_accepted() {
        long before = countRows("transactions");
        for (TransactionState state : ALL_STATES) {
            insertTransaction(state.name(), TransactionType.CASH_IN.name(), PaymentMethod.WALLET.name());
        }
        assertThat(countRows("transactions")).isEqualTo(before + ALL_STATES.size());
    }

    @Test
    void every_transaction_type_constant_is_accepted() {
        long before = countRows("transactions");
        for (TransactionType type : TransactionType.values()) {
            insertTransaction(TransactionState.INITIALIZED.name(), type.name(), PaymentMethod.WALLET.name());
        }
        assertThat(countRows("transactions")).isEqualTo(before + TransactionType.values().length);
    }

    @Test
    void every_payment_method_constant_is_accepted() {
        long before = countRows("transactions");
        for (PaymentMethod method : PaymentMethod.values()) {
            insertTransaction(TransactionState.INITIALIZED.name(), TransactionType.CASH_IN.name(), method.name());
        }
        assertThat(countRows("transactions")).isEqualTo(before + PaymentMethod.values().length);
    }

    @Test
    void every_operation_type_constant_is_accepted() {
        long before = countRows("operations");
        for (OperationType type : OperationType.values()) {
            insertOperation(type.name());
        }
        assertThat(countRows("operations")).isEqualTo(before + OperationType.values().length);
    }

    @Test
    void every_trigger_source_constant_is_accepted() {
        long before = countRows("trx_state_historics");
        for (TriggerSource source : TriggerSource.values()) {
            insertTrxStateHistoric(null, TransactionState.INITIALIZED.name(), source.name());
        }
        assertThat(countRows("trx_state_historics")).isEqualTo(before + TriggerSource.values().length);
    }

    @Test
    void every_transaction_state_constant_is_accepted_as_trx_state_historic_new_state() {
        long before = countRows("trx_state_historics");
        for (TransactionState state : ALL_STATES) {
            insertTrxStateHistoric(null, state.name(), null);
        }
        assertThat(countRows("trx_state_historics")).isEqualTo(before + ALL_STATES.size());
    }

    @Test
    void every_authorization_status_constant_is_accepted() {
        long before = countRows("authorization_records");
        for (AuthorizationStatus status : AuthorizationStatus.values()) {
            insertAuthorizationRecord(status.name());
        }
        assertThat(countRows("authorization_records")).isEqualTo(before + AuthorizationStatus.values().length);
    }
}