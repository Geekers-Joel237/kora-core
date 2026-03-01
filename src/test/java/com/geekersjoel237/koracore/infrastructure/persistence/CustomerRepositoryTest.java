package com.geekersjoel237.koracore.infrastructure.persistence;

import com.geekersjoel237.koracore.domain.enums.Role;
import com.geekersjoel237.koracore.domain.model.Customer;
import com.geekersjoel237.koracore.domain.model.User;
import com.geekersjoel237.koracore.domain.port.CustomerPinEncoder;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.infrastructure.persistence.entity.CustomerEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.entity.UserEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.JpaCustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private PostgresCustomerRepository repository;

    @Autowired
    private JpaCustomerRepository jpaRepository;

    @Autowired
    private CustomerPinEncoder pinEncoder;

    // ── helpers ──────────────────────────────────────────────────────────────

    private Customer buildAndSave(String email, String prefix, String number) {
        Id id = Id.generate();
        User user = User.create(id, "Test User", email, Role.CUSTOMER);
        PhoneNumber phone = PhoneNumber.of(prefix, number);
        Customer customer = Customer.create(user, phone, "1234", pinEncoder);
        repository.save(customer);
        return customer;
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void should_persist_and_return_intact_round_trip() {
        Customer customer = buildAndSave("alice@example.com", "+225", "07000000001");
        Id id = customer.snapshot().customerId();

        Optional<Customer> found = repository.findById(id);

        assertThat(found).isPresent();
        Customer.Snapshot snap = found.get().snapshot();
        assertThat(snap.customerId()).isEqualTo(id);
        assertThat(snap.user().email()).isEqualTo("alice@example.com");
        assertThat(snap.phoneNumber().fullNumber()).isEqualTo("+22507000000001");
    }

    @Test
    void should_find_customer_by_phone_number() {
        buildAndSave("bob@example.com", "+225", "07000000002");

        Optional<Customer> found = repository.findByPhoneNumber("+22507000000002");

        assertThat(found).isPresent();
        assertThat(found.get().snapshot().user().email()).isEqualTo("bob@example.com");
    }

    @Test
    void should_return_empty_when_phone_number_not_found() {
        Optional<Customer> found = repository.findByPhoneNumber("+22599999999999");
        assertThat(found).isEmpty();
    }

    @Test
    void should_find_customer_by_email() {
        buildAndSave("carol@example.com", "+225", "07000000003");

        Optional<Customer> found = repository.findByEmail("carol@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().snapshot().phoneNumber().number()).isEqualTo("07000000003");
    }

    @Test
    void should_return_empty_when_email_not_found() {
        Optional<Customer> found = repository.findByEmail("ghost@example.com");
        assertThat(found).isEmpty();
    }

    @Test
    void should_throw_constraint_violation_when_phone_number_is_duplicated() {
        buildAndSave("dave@example.com", "+225", "07000000004");

        // Attempt to save another customer with the same phone (prefix+number)
        Id id2 = Id.generate();
        UserEntity user2 = UserEntity.builder()
                .fullName("Dave Clone")
                .email("dave2@example.com")
                .role(com.geekersjoel237.koracore.domain.enums.Role.CUSTOMER)
                .status(com.geekersjoel237.koracore.domain.enums.UserStatus.VERIFIED)
                .build();
        user2.setId(id2.value());

        CustomerEntity duplicate = CustomerEntity.builder()
                .user(user2)
                .phonePrefix("+225")
                .phoneNumber("07000000004")
                .hashedPin("hashed")
                .build();
        duplicate.setId(id2.value());

        assertThatThrownBy(() -> jpaRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_throw_constraint_violation_when_email_is_duplicated() {
        buildAndSave("eve@example.com", "+225", "07000000005");

        Id id2 = Id.generate();
        UserEntity user2 = UserEntity.builder()
                .fullName("Eve Clone")
                .email("eve@example.com")
                .role(com.geekersjoel237.koracore.domain.enums.Role.CUSTOMER)
                .status(com.geekersjoel237.koracore.domain.enums.UserStatus.VERIFIED)
                .build();
        user2.setId(id2.value());

        CustomerEntity duplicate = CustomerEntity.builder()
                .user(user2)
                .phonePrefix("+225")
                .phoneNumber("07000000099")
                .hashedPin("hashed")
                .build();
        duplicate.setId(id2.value());

        assertThatThrownBy(() -> jpaRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_store_hashed_pin_not_plaintext() {
        Customer customer = buildAndSave("frank@example.com", "+225", "07000000006");
        Id id = customer.snapshot().customerId();

        // Load the raw entity to check the stored pin
        CustomerEntity entity = jpaRepository.findById(id.value()).orElseThrow();

        assertThat(entity.getHashedPin()).isNotEqualTo("1234");
        assertThat(entity.getHashedPin()).isNotBlank();
        assertThat(pinEncoder.matches("1234", entity.getHashedPin())).isTrue();
    }
}