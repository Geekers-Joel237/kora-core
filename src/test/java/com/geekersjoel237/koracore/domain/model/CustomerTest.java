package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.enums.Role;
import com.geekersjoel237.koracore.domain.port.CustomerPinEncoder;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.domain.vo.PhoneNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomerTest {

    private static final CustomerPinEncoder ENCODER = new CustomerPinEncoder() {
        @Override
        public String encode(String rawPin) {
            return "HASHED:" + rawPin;
        }

        @Override
        public boolean matches(String rawPin, String encodedPin) {
            return ("HASHED:" + rawPin).equals(encodedPin);
        }
    };

    private User user;
    private PhoneNumber phoneNumber;

    @BeforeEach
    void setUp() {
        user        = User.create(new Id("u-001"), "Jane Doe", "jane@example.com", Role.CUSTOMER);
        phoneNumber = PhoneNumber.of("+225", "0700000000");
    }

    // ── Construction ──────────────────────────────────────────────────────────

    @Test
    void should_create_customer_with_hashed_pin() {
        Customer customer = Customer.create(user, phoneNumber, "1234", ENCODER);
        assertNotEquals("1234", customer.snapshot().hashedPin());
    }

    @Test
    void should_delegate_id_to_user() {
        Customer customer = Customer.create(user, phoneNumber, "1234", ENCODER);
        assertEquals(user.snapshot().id(), customer.snapshot().customerId());
    }

    @Test
    void should_delegate_active_status_to_user() {
        Customer customer = Customer.create(user, phoneNumber, "1234", ENCODER);
        assertEquals(user.isActive(), customer.isActive());
    }

    @Test
    void should_delegate_suspended_status_to_user() {
        Customer customer = Customer.create(user, phoneNumber, "1234", ENCODER);
        user.suspend();
        assertTrue(customer.isSuspended());
    }

    // ── matchesPin ────────────────────────────────────────────────────────────

    @Test
    void should_return_true_when_pin_matches() {
        Customer customer = Customer.create(user, phoneNumber, "1234", ENCODER);
        assertTrue(customer.matchesPin("1234", ENCODER));
    }

    @Test
    void should_return_false_when_pin_does_not_match() {
        Customer customer = Customer.create(user, phoneNumber, "1234", ENCODER);
        assertFalse(customer.matchesPin("9999", ENCODER));
    }

    // ── Validation construction ───────────────────────────────────────────────

    @Test
    void should_throw_when_user_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> Customer.create(null, phoneNumber, "1234", ENCODER));
    }

    @Test
    void should_throw_when_phone_number_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> Customer.create(user, null, "1234", ENCODER));
    }

    @Test
    void should_throw_when_pin_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> Customer.create(user, phoneNumber, null, ENCODER));
    }

    @Test
    void should_throw_when_pin_is_blank() {
        assertThrows(IllegalArgumentException.class,
                () -> Customer.create(user, phoneNumber, "", ENCODER));
    }

    // ── Snapshot — reconstruction ─────────────────────────────────────────────

    @Test
    void should_reconstruct_customer_from_snapshot_without_validation() {
        Customer original = Customer.create(user, phoneNumber, "1234", ENCODER);
        Customer rebuilt  = Customer.createFromSnapshot(original.snapshot());
        assertEquals(original.snapshot().customerId(), rebuilt.snapshot().customerId());
    }
}