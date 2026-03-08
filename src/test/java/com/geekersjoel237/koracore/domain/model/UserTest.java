package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.enums.Role;
import com.geekersjoel237.koracore.domain.enums.UserStatus;
import com.geekersjoel237.koracore.domain.vo.Id;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    private static final Id VALID_ID      = new Id("001");
    private static final String FULL_NAME = "John Doe";
    private static final String EMAIL     = "john@example.com";
    private static final Role ROLE        = Role.CUSTOMER;

    // ── Construction valide ───────────────────────────────────────────────────

    @Test
    void should_create_user_with_verified_status_by_default() {
        User user = User.create(VALID_ID, FULL_NAME, EMAIL, ROLE);
        assertEquals(UserStatus.VERIFIED, user.snapshot().status());
    }

    // ── Validation construction ───────────────────────────────────────────────

    @Test
    void should_throw_when_id_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> User.create(null, FULL_NAME, EMAIL, ROLE));
    }

    @Test
    void should_throw_when_fullname_is_blank() {
        assertThrows(IllegalArgumentException.class,
                () -> User.create(VALID_ID, "", EMAIL, ROLE));
    }

    @Test
    void should_throw_when_email_is_invalid() {
        assertThrows(IllegalArgumentException.class,
                () -> User.create(VALID_ID, FULL_NAME, "not-an-email", ROLE));
    }

    @Test
    void should_throw_when_role_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> User.create(VALID_ID, FULL_NAME, EMAIL, null));
    }

    // ── Comportement métier ───────────────────────────────────────────────────

    @Test
    void should_return_true_when_user_is_active() {
        User user = User.create(VALID_ID, FULL_NAME, EMAIL, ROLE);
        assertTrue(user.isActive());
    }

    @Test
    void should_return_false_when_user_is_suspended() {
        User user = User.create(VALID_ID, FULL_NAME, EMAIL, ROLE);
        user.suspend();
        assertFalse(user.isActive());
    }

    @Test
    void should_return_true_when_user_is_suspended() {
        User user = User.create(VALID_ID, FULL_NAME, EMAIL, ROLE);
        user.suspend();
        assertTrue(user.isSuspended());
    }

    @Test
    void should_return_true_when_user_is_verified_after_verify() {
        User user = User.create(VALID_ID, FULL_NAME, EMAIL, ROLE);
        user.suspend();
        user.verify();
        assertTrue(user.isActive());
    }

    // ── Snapshot — reconstruction ─────────────────────────────────────────────

    @Test
    void should_reconstruct_user_from_snapshot_without_validation() {
        User original = User.create(VALID_ID, FULL_NAME, EMAIL, ROLE);
        User rebuilt  = User.createFromSnapshot(original.snapshot());
        assertEquals(original.snapshot(), rebuilt.snapshot());
    }

    @Test
    void should_reconstruct_suspended_user_from_snapshot() {
        User user = User.create(VALID_ID, FULL_NAME, EMAIL, ROLE);
        user.suspend();
        User rebuilt = User.createFromSnapshot(user.snapshot());
        assertEquals(UserStatus.SUSPENDED, rebuilt.snapshot().status());
    }
}