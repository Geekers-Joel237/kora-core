package com.geekersjoel237.koracore.auth.integration.persistence;

import com.geekersjoel237.koracore.auth.domain.enums.Role;
import com.geekersjoel237.koracore.auth.domain.enums.UserStatus;
import com.geekersjoel237.koracore.shared.integration.persistence.AbstractRepositoryTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.geekersjoel237.koracore.shared.integration.persistence.EnumColumnConstraint.allowedValues;
import static com.geekersjoel237.koracore.shared.integration.persistence.EnumColumnConstraint.namesOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnumColumnConstraintsDbTest extends AbstractRepositoryTest {

    @PersistenceContext
    private EntityManager em;

    @Test
    void users_role_accepts_exactly_the_roles() {
        assertThat(allowedValues(em, "users", "ck_users_role"))
                .isEqualTo(namesOf(Role.values()));
    }

    @Test
    void users_status_accepts_exactly_the_user_statuses() {
        assertThat(allowedValues(em, "users", "ck_users_status"))
                .isEqualTo(namesOf(UserStatus.values()));
    }

    /**
     * Native SQL on purpose: a save through the repository would only prove that
     * Hibernate refuses to map an unknown constant. The row has to reach the database
     * for the constraint to be the thing that says no.
     */
    @Test
    void the_database_refuses_a_status_the_domain_does_not_define() {
        assertThatThrownBy(() -> insertUserWithStatus("SHADOW_BANNED"))
                .hasStackTraceContaining("violates check constraint")
                .hasStackTraceContaining("ck_users_status");
    }

    private void insertUserWithStatus(String status) {
        String id = UUID.randomUUID().toString();
        em.createNativeQuery("""
                        INSERT INTO users
                            (id, full_name, email, role, status, created_at, updated_at)
                        VALUES
                            (:id, 'Constraint Probe', :email, 'CUSTOMER', :status, now(), now())
                        """)
                .setParameter("id", id)
                .setParameter("email", id + "@example.com")
                .setParameter("status", status)
                .executeUpdate();
    }
}
